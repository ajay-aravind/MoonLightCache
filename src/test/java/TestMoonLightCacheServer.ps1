
param(
    [Parameter(Mandatory = $true, Position = 0)]
    [ValidateSet("GET", "SET", "EXISTS", "DELETE")]
    [string]$Operation,

    [Parameter(Mandatory = $true, Position = 1)]
    [string]$Key,

    [Parameter(Position = 2)]
    [string]$Value,

# -1 means no expiration. Otherwise, specify hours.
    [Parameter(Position = 3)]
    [double]$ExpirationHours = -1,

    [Parameter(Position = 4)]
    [ValidateSet("stringType", "integerType", "longType", "boolType", "characterType")]
    [string]$DataType = "stringType"
)

$ErrorActionPreference = "Stop"

$HostName = "127.0.0.1"
$Port = 40001
$Utf8 = [System.Text.Encoding]::UTF8

# Verify the operation-specific arguments.
if ([string]::IsNullOrEmpty($Key)) {
    throw "Key cannot be empty."
}

if ($Operation -eq "SET") {
    if ($null -eq $Value) {
        throw "SET requires -Value."
    }

    if ($ExpirationHours -lt 0 -and $ExpirationHours -ne -1) {
        throw "ExpirationHours must be -1 (no expiry) or nonnegative."
    }
}

# These numeric codes MUST match your Java enums.
$OperationCodes = @{
    SET    = 1
    GET    = 2
    EXISTS = 3
    DELETE = 4
}

$DataTypeCodes = @{
    stringType    = 1
    integerType   = 2
    longType      = 3
    boolType      = 4
    characterType = 5
}

$ResponseNames = @{
    1 = "EXISTS"
    2 = "NOTEXISTS"
    3 = "CREATED"
    4 = "FOUND"
    5 = "FAILED"
    6 = "DELETED"
    7 = "INVALID_OPERATION"
}

# Return bytes in big-endian order.
function Get-BigEndianBytes {
    param([byte[]]$Bytes)

    [byte[]]$Result = $Bytes.Clone()

    if ([BitConverter]::IsLittleEndian) {
        [Array]::Reverse($Result)
    }

    return ,$Result
}

function Get-UInt16BE {
    param([int]$Number)

    return ,([byte[]]@(
        [byte](($Number -shr 8) -band 0xFF),
        [byte]($Number -band 0xFF)
    ))
}

function Get-UInt32BE {
    param([long]$Number)

    return ,([byte[]]@(
        [byte](($Number -shr 24) -band 0xFF),
        [byte](($Number -shr 16) -band 0xFF),
        [byte](($Number -shr 8) -band 0xFF),
        [byte]($Number -band 0xFF)
    ))
}

function Get-Int64BE {
    param([long]$Number)

    return ,(Get-BigEndianBytes ([BitConverter]::GetBytes($Number)))
}

function Format-Hex {
    param([byte[]]$Bytes)

    $Parts = foreach ($B in $Bytes) {
        $B.ToString("X2")
    }

    return [string]::Join(" ", $Parts)
}

function Read-Exact {
    param(
        [System.IO.Stream]$Stream,
        [int]$Count
    )

    [byte[]]$Buffer = New-Object byte[] $Count
    $Offset = 0

    while ($Offset -lt $Count) {
        $Read = $Stream.Read($Buffer, $Offset, $Count - $Offset)

        if ($Read -eq 0) {
            throw "Server closed the connection before the frame was complete."
        }

        $Offset += $Read
    }

    return ,$Buffer
}

# Encode the value according to DataType.
function Convert-ValueToBytes {
    param(
        [string]$Value,
        [string]$Type
    )

    switch ($Type) {
        "stringType" {
            return ,([byte[]]$Utf8.GetBytes($Value))
        }

        "integerType" {
            $Number = 0
            if (-not [int]::TryParse(
                    $Value,
                    [Globalization.NumberStyles]::Integer,
                    [Globalization.CultureInfo]::InvariantCulture,
                    [ref]$Number
            )) {
                throw "integerType requires a valid 32-bit integer."
            }

            return ,(Get-BigEndianBytes (
            [BitConverter]::GetBytes([int]$Number)
            ))
        }

        "longType" {
            $Number = [long]0
            if (-not [long]::TryParse(
                    $Value,
                    [Globalization.NumberStyles]::Integer,
                    [Globalization.CultureInfo]::InvariantCulture,
                    [ref]$Number
            )) {
                throw "longType requires a valid 64-bit integer."
            }

            return ,(Get-Int64BE $Number)
        }

        "boolType" {
            if ($Value -notin @("true", "false")) {
                throw "boolType accepts only true or false."
            }

            if ($Value -eq "true") {
                return ,([byte[]]@(1))
            }

            return ,([byte[]]@(0))
        }

        "characterType" {
            # Java char: one UTF-16 code unit, encoded as 2 big-endian bytes.
            if ($Value.Length -ne 1) {
                throw "characterType requires exactly one UTF-16 character."
            }

            $CodeUnit = [int][char]$Value

            return ,([byte[]]@(
                [byte](($CodeUnit -shr 8) -band 0xFF),
                [byte]($CodeUnit -band 0xFF)
            ))
        }
    }
}

# Decode values returned in FOUND responses.
function Convert-BytesToValue {
    param(
        [byte[]]$Bytes,
        [int]$TypeCode
    )

    switch ($TypeCode) {
        1 {
            return $Utf8.GetString($Bytes)
        }

        2 {
            if ($Bytes.Length -ne 4) {
                throw "Invalid integerType response: expected 4 bytes."
            }

            [long]$Number =
            ([long]$Bytes[0] * 16777216) +
                    ([long]$Bytes[1] * 65536) +
                    ([long]$Bytes[2] * 256) +
                    [long]$Bytes[3]

            if ($Number -ge 2147483648) {
                $Number -= 4294967296
            }

            return [int]$Number
        }

        3 {
            if ($Bytes.Length -ne 8) {
                throw "Invalid longType response: expected 8 bytes."
            }

            [byte[]]$LittleEndian = Get-BigEndianBytes $Bytes
            return [BitConverter]::ToInt64($LittleEndian, 0)
        }

        4 {
            if ($Bytes.Length -ne 1 -or
                    ($Bytes[0] -ne 0 -and $Bytes[0] -ne 1)) {
                throw "Invalid boolType response."
            }

            return ($Bytes[0] -eq 1)
        }

        5 {
            if ($Bytes.Length -ne 2) {
                throw "Invalid characterType response: expected 2 bytes."
            }

            $CodeUnit = ([int]$Bytes[0] * 256) + [int]$Bytes[1]
            return [string][char]$CodeUnit
        }

        default {
            throw "Unknown response data type code: $TypeCode"
        }
    }
}

# ------------------------------------------------------------
# Construct request
# ------------------------------------------------------------

[byte[]]$KeyBytes = $Utf8.GetBytes($Key)

if ($KeyBytes.Length -eq 0 -or $KeyBytes.Length -gt 65535) {
    throw "Key must contain between 1 and 65535 UTF-8 bytes."
}

$OperationCode = $OperationCodes[$Operation]

# Payload starts with operation + key length + key.
$Payload = [System.Collections.Generic.List[byte]]::new()

$Payload.Add([byte]$OperationCode)
$Payload.AddRange((Get-UInt16BE $KeyBytes.Length))
$Payload.AddRange($KeyBytes)

if ($Operation -eq "SET") {
    [byte[]]$ValueBytes = Convert-ValueToBytes $Value $DataType

    if ($ValueBytes.Length -gt 1048576) {
        throw "Value exceeds the 1 MiB limit."
    }

    $Payload.Add([byte]$DataTypeCodes[$DataType])
    $Payload.AddRange((Get-UInt32BE $ValueBytes.Length))
    $Payload.AddRange($ValueBytes)

    # Convert expiration hours to milliseconds.
    if ($ExpirationHours -eq -1) {
        [long]$TtlMillis = -1
    }
    else {
        if ($ExpirationHours -gt 2562047788) {
            throw "ExpirationHours is too large."
        }

        [long]$TtlMillis = [Math]::Round(
                $ExpirationHours * 60000,
                [MidpointRounding]::AwayFromZero)

        [long]$TtlMillis = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds() + $TtlMillis

    }

    $Payload.AddRange((Get-Int64BE $TtlMillis))
}

if ($Payload.Count -gt 1048576) {
    throw "Request frame exceeds the 1 MiB limit."
}

[byte[]]$PayloadBytes = $Payload.ToArray()

$Frame = [System.Collections.Generic.List[byte]]::new()
$Frame.AddRange((Get-UInt32BE $PayloadBytes.Length))
$Frame.AddRange($PayloadBytes)

[byte[]]$Request = $Frame.ToArray()

# ------------------------------------------------------------
# Connect, send request, receive response
# ------------------------------------------------------------

$Client = [System.Net.Sockets.TcpClient]::new()

try {
    Write-Host "Connecting to ${HostName}:${Port}..."
    $Client.Connect($HostName, $Port)

    $Stream = $Client.GetStream()
    $Stream.ReadTimeout = 5000
    $Stream.WriteTimeout = 5000

    Write-Host "`nRequest"
    Write-Host "  Operation: $Operation ($OperationCode)"
    Write-Host "  Key:       $Key"
    Write-Host "  Key bytes: $($KeyBytes.Length)"

    if ($Operation -eq "SET") {
        Write-Host "  Data type: $DataType"
        Write-Host "  Value:     $Value"
        Write-Host "  TTL:       $TtlMillis ms"
    }

    Write-Host "  Frame hex: $Request"

    $Stream.Write($Request, 0, $Request.Length)
    $Stream.Flush()

    # Read response frame length.
    [byte[]]$Prefix = Read-Exact $Stream 4

    [long]$ResponseLength =
    ([long]$Prefix[0] * 16777216) +
            ([long]$Prefix[1] * 65536) +
            ([long]$Prefix[2] * 256) +
            [long]$Prefix[3]

    if ($ResponseLength -lt 3 -or $ResponseLength -gt 1048576) {
        throw "Invalid response frame length: $ResponseLength"
    }

    [byte[]]$ResponsePayload = Read-Exact $Stream ([int]$ResponseLength)
    [byte[]]$ResponseFrame = $Prefix + $ResponsePayload

    Write-Host "`nResponse"
    Write-Host "  Payload length: $ResponseLength"
    Write-Host "  Frame hex:      $ResponseFrame"

    if ($ResponsePayload.Length -lt 3) {
        throw "Response payload is too short."
    }

    $ResponseCode = [int]$ResponsePayload[0]
    $ResponseKeyLength =
    ([int]$ResponsePayload[1] * 256) +
            [int]$ResponsePayload[2]

    if ($ResponseKeyLength -eq 0 -or
            3 + $ResponseKeyLength -gt $ResponsePayload.Length) {
        throw "Invalid response key length."
    }

    [byte[]]$ResponseKeyBytes = [byte[]]$ResponsePayload[
    3..(2 + $ResponseKeyLength)
    ]

    $ResponseKey = $Utf8.GetString($ResponseKeyBytes)

    $ResponseName = $ResponseNames[$ResponseCode]
    if ($null -eq $ResponseName) {
        $ResponseName = "UNKNOWN"
    }

    Write-Host "  Response code: $ResponseCode ($ResponseName)"
    Write-Host "  Response key:  '$ResponseKey'"

    if ($ResponseCode -eq 4) {
        $TypeOffset = 3 + $ResponseKeyLength

        if ($ResponsePayload.Length -lt $TypeOffset + 5) {
            throw "Truncated FOUND response."
        }

        $TypeCode = [int]$ResponsePayload[$TypeOffset]
        $ValueLengthOffset = $TypeOffset + 1

        [long]$ReturnedValueLength =
        ([long]$ResponsePayload[$ValueLengthOffset] * 16777216) +
                ([long]$ResponsePayload[$ValueLengthOffset + 1] * 65536) +
                ([long]$ResponsePayload[$ValueLengthOffset + 2] * 256) +
                [long]$ResponsePayload[$ValueLengthOffset + 3]

        $ValueOffset = $ValueLengthOffset + 4

        if ($ReturnedValueLength -gt 1048576 -or
                $ReturnedValueLength -ne
                        ($ResponsePayload.Length - $ValueOffset)) {
            throw "Invalid FOUND response value length."
        }

        [byte[]]$ReturnedValueBytes = [byte[]]@()

        if ($ReturnedValueLength -gt 0) {
            $ReturnedValueBytes = [byte[]]$ResponsePayload[
            $ValueOffset..($ValueOffset + $ReturnedValueLength - 1)
            ]
        }

        $ReturnedTypeName = $null
        foreach ($Name in $DataTypeCodes.Keys) {
            if ($DataTypeCodes[$Name] -eq $TypeCode) {
                $ReturnedTypeName = $Name
                break
            }
        }

        Write-Host "  Data type:     $ReturnedTypeName"
        Write-Host "  Value length:  $ReturnedValueLength bytes"
        Write-Host "  Value:         $(Convert-BytesToValue $ReturnedValueBytes $TypeCode)"
    }
}
catch {
    Write-Host "`nERROR: $($_.Exception.Message)" -ForegroundColor Red
}
finally {
    $Client.Dispose()
}
