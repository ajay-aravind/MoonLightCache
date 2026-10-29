
param(
    [string]$HostName = "127.0.0.1",
    [int]$Port = 40001
)

$ErrorActionPreference = "Stop"

# Must match the numeric codes in your Java enums.
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

$Utf8 = [System.Text.Encoding]::UTF8
$script:Passed = 0
$script:Failed = 0
$script:RunId = [Guid]::NewGuid().ToString("N").Substring(0, 8)

# ------------------------------------------------------------
# Binary encoding helpers
# ------------------------------------------------------------

function Format-Hex {
    param([byte[]]$Bytes)

    $Parts = foreach ($B in $Bytes) {
        $B.ToString("X2")
    }

    return [string]::Join(" ", $Parts)
}

function Get-U16BE {
    param([int]$Number)

    return ,([byte[]]@(
        [byte](($Number -shr 8) -band 255),
        [byte]($Number -band 255)
    ))
}

function Get-U32BE {
    param([long]$Number)

    return ,([byte[]]@(
        [byte](($Number -shr 24) -band 255),
        [byte](($Number -shr 16) -band 255),
        [byte](($Number -shr 8) -band 255),
        [byte]($Number -band 255)
    ))
}

function Get-I64BE {
    param([long]$Number)

    [byte[]]$Bytes = [BitConverter]::GetBytes($Number)

    if ([BitConverter]::IsLittleEndian) {
        [Array]::Reverse($Bytes)
    }

    return ,$Bytes
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
            throw "Server closed connection mid-frame."
        }

        $Offset += $Read
    }

    return ,$Buffer
}

function New-Frame {
    param([byte[]]$Payload)

    $Frame = [System.Collections.Generic.List[byte]]::new()
    $Frame.AddRange((Get-U32BE $Payload.Length))
    $Frame.AddRange($Payload)

    return ,$Frame.ToArray()
}

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
            $Number = [int]0

            if (-not [int]::TryParse(
                    $Value,
                    [Globalization.NumberStyles]::Integer,
                    [Globalization.CultureInfo]::InvariantCulture,
                    [ref]$Number
            )) {
                throw "Invalid integerType test value: $Value"
            }

            [byte[]]$Bytes = [BitConverter]::GetBytes($Number)

            if ([BitConverter]::IsLittleEndian) {
                [Array]::Reverse($Bytes)
            }

            return ,$Bytes
        }

        "longType" {
            $Number = [long]0

            if (-not [long]::TryParse(
                    $Value,
                    [Globalization.NumberStyles]::Integer,
                    [Globalization.CultureInfo]::InvariantCulture,
                    [ref]$Number
            )) {
                throw "Invalid longType test value: $Value"
            }

            return ,(Get-I64BE $Number)
        }

        "boolType" {
            if ($Value -eq "true") {
                return ,([byte[]]@(1))
            }

            if ($Value -eq "false") {
                return ,([byte[]]@(0))
            }

            throw "Invalid boolType test value: $Value"
        }

        "characterType" {
            if ($Value.Length -ne 1) {
                throw "characterType test value must be one UTF-16 code unit."
            }

            $CodeUnit = [int][char]$Value

            return ,([byte[]]@(
                [byte](($CodeUnit -shr 8) -band 255),
                [byte]($CodeUnit -band 255)
            ))
        }

        default {
            throw "Unsupported data type: $Type"
        }
    }
}

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
                throw "Invalid integerType response length."
            }

            [long]$N =
            ([long]$Bytes[0] * 16777216) +
                    ([long]$Bytes[1] * 65536) +
                    ([long]$Bytes[2] * 256) +
                    [long]$Bytes[3]

            if ($N -ge 2147483648) {
                $N -= 4294967296
            }

            return [int]$N
        }

        3 {
            if ($Bytes.Length -ne 8) {
                throw "Invalid longType response length."
            }

            [byte[]]$Copy = $Bytes.Clone()

            if ([BitConverter]::IsLittleEndian) {
                [Array]::Reverse($Copy)
            }

            return [BitConverter]::ToInt64($Copy, 0)
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
                throw "Invalid characterType response length."
            }

            $CodeUnit = ([int]$Bytes[0] * 256) + [int]$Bytes[1]
            return [string][char]$CodeUnit
        }

        default {
            throw "Unknown data type code: $TypeCode"
        }
    }
}

# ------------------------------------------------------------
# Request encoding
# ------------------------------------------------------------

function New-RequestFrame {
    param(
        [string]$Operation,
        [string]$Key,
        [string]$Value,
        [string]$Type,
        [long]$TtlMillis = -1
    )

    [byte[]]$KeyBytes = $Utf8.GetBytes($Key)

    if ($KeyBytes.Length -eq 0 -or $KeyBytes.Length -gt 65535) {
        throw "Key must contain between 1 and 65535 UTF-8 bytes."
    }

    $Payload = [System.Collections.Generic.List[byte]]::new()

    $Payload.Add([byte]$OperationCodes[$Operation])
    $Payload.AddRange((Get-U16BE $KeyBytes.Length))
    $Payload.AddRange($KeyBytes)

    if ($Operation -eq "SET") {
        [byte[]]$ValueBytes = Convert-ValueToBytes $Value $Type

        if ($ValueBytes.Length -gt 1048576) {
            throw "Value exceeds the 1 MiB limit."
        }

        $Payload.Add([byte]$DataTypeCodes[$Type])
        $Payload.AddRange((Get-U32BE $ValueBytes.Length))
        $Payload.AddRange($ValueBytes)
        $Payload.AddRange((Get-I64BE $TtlMillis))
    }

    if ($Payload.Count -gt 1048576) {
        throw "Request exceeds the 1 MiB frame limit."
    }

    return ,(New-Frame $Payload.ToArray())
}

# ------------------------------------------------------------
# Response decoding
# ------------------------------------------------------------

function Decode-Response {
    param([byte[]]$Payload)

    if ($Payload.Length -lt 3) {
        throw "Response payload is too short."
    }

    $Code = [int]$Payload[0]
    $KeyLength = ([int]$Payload[1] * 256) + [int]$Payload[2]

    if ($KeyLength -eq 0 -or 3 + $KeyLength -gt $Payload.Length) {
        throw "Invalid response key length."
    }

    [byte[]]$KeyBytes = New-Object byte[] $KeyLength

    [Array]::Copy($Payload, 3, $KeyBytes, 0, $KeyLength)

    $Key = $Utf8.GetString($KeyBytes)

    $TypeCode = $null
    $ValueBytes = $null
    $Value = $null

    if ($Code -eq 4 -or $Code -eq 3) {
        $TypeOffset = 3 + $KeyLength

        if ($Payload.Length -lt $TypeOffset + 5) {
            throw "Truncated FOUND response."
        }

        $TypeCode = [int]$Payload[$TypeOffset]
        $LengthOffset = $TypeOffset + 1

        [long]$ValueLength =
        ([long]$Payload[$LengthOffset] * 16777216) +
                ([long]$Payload[$LengthOffset + 1] * 65536) +
                ([long]$Payload[$LengthOffset + 2] * 256) +
                [long]$Payload[$LengthOffset + 3]

        $ValueOffset = $LengthOffset + 4

        if ($ValueLength -gt 1048576 -or
                $ValueLength -ne ($Payload.Length - $ValueOffset)) {
            throw "FOUND response has an invalid value length."
        }

        $ValueBytes = New-Object byte[] ([int]$ValueLength)

        if ($ValueLength -gt 0) {
            [Array]::Copy(
                    $Payload, $ValueOffset,
                    $ValueBytes, 0, [int]$ValueLength
            )
        }

        $Value = Convert-BytesToValue $ValueBytes $TypeCode
    }
    elseif ($Payload.Length -ne 3 + $KeyLength) {
        throw "Unexpected fields in $($ResponseNames[$Code]) response."
    }

    $Name = $ResponseNames[$Code]

    if ($null -eq $Name) {
        $Name = "UNKNOWN"
    }

    return [pscustomobject]@{
        Code      = $Code
        Name      = $Name
        Key       = $Key
        TypeCode  = $TypeCode
        Value     = $Value
        ValueBytes = $ValueBytes
    }
}

# ------------------------------------------------------------
# Send one request, read one response
# ------------------------------------------------------------

function Invoke-CacheRequest {
    param(
        [string]$Operation,
        [string]$Key,
        [string]$Value,
        [string]$Type,
        [long]$TtlMillis = -1
    )

    [byte[]]$Request = New-RequestFrame `
        -Operation $Operation `
        -Key $Key `
        -Value $Value `
        -Type $Type `
        -TtlMillis $TtlMillis

    $Client = [System.Net.Sockets.TcpClient]::new()

    try {
        $Client.ReceiveTimeout = 5000
        $Client.SendTimeout = 5000
        $Client.Connect($HostName, $Port)

        $Stream = $Client.GetStream()
        $Stream.ReadTimeout = 5000
        $Stream.WriteTimeout = 5000

        # Wrap TCP in TLS. Certificate validation remains enabled.
        $TlsStream = [System.Net.Security.SslStream]::new(
                $Stream,
                $false
        )

        # Authenticate the server and validate its certificate/hostname.
        $TlsStream.AuthenticateAsClient($HostName)

        Write-Host "TLS connection established."
        Write-Host "Protocol: $($TlsStream.SslProtocol)"
        Write-Host "Cipher:   $($TlsStream.CipherAlgorithm)"
        Write-Host "Strength: $($TlsStream.CipherStrength)"


        $TlsStream.Write($Request, 0, $Request.Length)
        $TlsStream.Flush()

        [byte[]]$Prefix = Read-Exact $TlsStream 4

        [long]$Length =
        ([long]$Prefix[0] * 16777216) +
                ([long]$Prefix[1] * 65536) +
                ([long]$Prefix[2] * 256) +
                [long]$Prefix[3]

        if ($Length -lt 3 -or $Length -gt 1048576) {
            throw "Invalid response frame length: $Length"
        }

        [byte[]]$Payload = Read-Exact $TlsStream ([int]$Length)

        return (Decode-Response $Payload)
    }
    finally {
        $Client.Dispose()
    }
}

# ------------------------------------------------------------
# Test runner
# ------------------------------------------------------------

function Assert-Equal {
    param(
        $Actual,
        $Expected,
        [string]$Description
    )

    if ($Actual -cne $Expected) {
        throw "$Description : expected '$Expected', got '$Actual'."
    }
}

function Assert-BytesEqual {
    param(
        [byte[]]$Actual,
        [byte[]]$Expected,
        [string]$Description
    )

    if ($null -eq $Actual -or $null -eq $Expected) {
        throw "$Description : byte array is null."
    }

    if ([Convert]::ToBase64String($Actual) -cne
            [Convert]::ToBase64String($Expected)) {
        throw "$Description : byte arrays differ."
    }
}

function Test-Case {
    param(
        [string]$Name,
        [scriptblock]$Body
    )

    try {
        & $Body
        $script:Passed++
        Write-Host "[PASS] $Name" -ForegroundColor Green
    }
    catch {
        $script:Failed++
        Write-Host "[FAIL] $Name -- $($_.Exception.Message)" -ForegroundColor Red
    }
}

function Assert-Response {
    param(
        $Response,
        [int]$ExpectedCode,
        [string]$ExpectedKey
    )

    Assert-Equal $Response.Code $ExpectedCode "Response code"
    Assert-Equal $Response.Key $ExpectedKey "Response key"
}

# ------------------------------------------------------------
# Malformed protocol tests
# Expect the server to close the connection without a response.
# ------------------------------------------------------------

function Test-InvalidFrameRejected {
    param(
        [string]$Name,
        [byte[]]$Frame
    )

    Test-Case $Name {
        $Client = [System.Net.Sockets.TcpClient]::new()

        try {
            $Client.ReceiveTimeout = 2000
            $Client.Connect($HostName, $Port)

            $Stream = $Client.GetStream()
            $Stream.ReadTimeout = 2000

            # Wrap TCP in TLS. Certificate validation remains enabled.
            $TlsStream = [System.Net.Security.SslStream]::new(
                    $Stream,
                    $false
            )

            # Authenticate the server and validate its certificate/hostname.
            $TlsStream.AuthenticateAsClient($HostName)

            Write-Host "TLS connection established."
            Write-Host "Protocol: $($TlsStream.SslProtocol)"
            Write-Host "Cipher:   $($TlsStream.CipherAlgorithm)"
            Write-Host "Strength: $($TlsStream.CipherStrength)"

            $TlsStream.Write($Frame, 0, $Frame.Length)
            $TlsStream.Flush()

            $FirstByte = $TlsStream.ReadByte()

            if ($FirstByte -ne -1) {
                throw "Expected server to close connection; received a response byte."
            }
        }
        finally {
            $Client.Dispose()
        }
    }
}

# ------------------------------------------------------------
# Main suite
# ------------------------------------------------------------

Write-Host ""
Write-Host "MoonLightCache integration tests"
Write-Host "Server: ${HostName}:${Port}"
Write-Host "Run ID: $script:RunId"
Write-Host ""

try {
    $ProbeKey = "health-$script:RunId"

    # A connection failure here is a suite setup failure.
    $Probe = Invoke-CacheRequest `
        -Operation "EXISTS" -Key $ProbeKey `
        -Value $null -Type $null

    Write-Host "Server connection successful."
}
catch {
    Write-Host "Cannot communicate with the cache server: $($_.Exception.Message)" `
        -ForegroundColor Red
    exit 2
}

# Missing-key behavior.
$MissingKey = "missing-$script:RunId"

Test-Case "GET missing key returns NOTEXISTS" {
    $R = Invoke-CacheRequest GET $MissingKey $null $null
    Assert-Response $R 2 $MissingKey
}

Test-Case "EXISTS missing key returns NOTEXISTS" {
    $R = Invoke-CacheRequest EXISTS $MissingKey $null $null
    Assert-Response $R 2 $MissingKey
}

# Values deliberately exercise different binary encodings.
$Scenarios = @(
    @{ Type = "stringType";    Value = "hello-$script:RunId" },
    @{ Type = "integerType";   Value = "-123456789" },
    @{ Type = "longType";      Value = "9876543210123" },
    @{ Type = "boolType";      Value = "True" },
    @{ Type = "characterType"; Value = "Q" }
)

foreach ($Scenario in $Scenarios) {
    $Type = $Scenario.Type
    $Value = $Scenario.Value
    $Key = "test-$($Type)-$script:RunId"
    $ExpectedBytes = Convert-ValueToBytes $Value $Type
    $TypeCode = $DataTypeCodes[$Type]

    Write-Host ""
    Write-Host "Testing $Type with key '$Key'"

    #key expires 5 minutes from now
    [long]$TtlMillis = [Math]::Round(
            5 * 60000,
            [MidpointRounding]::AwayFromZero)

    [long]$TtlMillis = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds() + $TtlMillis

    # SET -> CREATED
    Test-Case "$Type SET returns CREATED" {
        $R = Invoke-CacheRequest `
            SET $Key $Value $Type $TtlMillis

        Assert-Response $R 3 $Key
    }

    # GET -> FOUND, same type and exact bytes
    Test-Case "$Type GET returns expected value" {
        $R = Invoke-CacheRequest GET $Key $null $null

        Assert-Response $R 4 $Key
        Assert-Equal $R.TypeCode $TypeCode "Data type code"
        Assert-Equal ([string]$R.Value) ([string]$Value) "Decoded value"
        Assert-BytesEqual $R.ValueBytes $ExpectedBytes "Encoded value"
    }

    # EXISTS -> EXISTS
    Test-Case "$Type EXISTS returns EXISTS" {
        $R = Invoke-CacheRequest EXISTS $Key $null $null
        Assert-Response $R 1 $Key
    }

    # DELETE -> DELETED
    Test-Case "$Type DELETE returns DELETED" {
        $R = Invoke-CacheRequest DELETE $Key $null $null
        Assert-Response $R 6 $Key
    }

    # EXISTS after DELETE -> NOTEXISTS
    Test-Case "$Type EXISTS after DELETE returns NOTEXISTS" {
        $R = Invoke-CacheRequest EXISTS $Key $null $null
        Assert-Response $R 2 $Key
    }

    # GET after DELETE -> NOTEXISTS
    Test-Case "$Type GET after DELETE returns NOTEXISTS" {
        $R = Invoke-CacheRequest GET $Key $null $null
        Assert-Response $R 2 $Key
    }
}

# Invalid protocol: unknown operation code.
$UnknownOperationPayload = [byte[]]@(255, 0, 1, 120)
$UnknownOperationFrame = New-Frame $UnknownOperationPayload

Test-InvalidFrameRejected `
    "Unknown operation code is rejected" `
    $UnknownOperationFrame

# Invalid protocol: SET boolType with invalid byte 2.
$InvalidBoolPayload = [System.Collections.Generic.List[byte]]::new()
$InvalidBoolPayload.Add([byte]$OperationCodes.SET)

$InvalidKeyBytes = $Utf8.GetBytes("invalid-bool")
$InvalidBoolPayload.AddRange((Get-U16BE $InvalidKeyBytes.Length))
$InvalidBoolPayload.AddRange($InvalidKeyBytes)
$InvalidBoolPayload.Add([byte]$DataTypeCodes.boolType)
$InvalidBoolPayload.AddRange((Get-U32BE 1))
$InvalidBoolPayload.Add([byte]2)
$InvalidBoolPayload.AddRange((Get-I64BE 60000))

Test-InvalidFrameRejected `
    "Invalid boolean byte is rejected" `
    (New-Frame $InvalidBoolPayload.ToArray())

# Invalid protocol: empty key.
$EmptyKeyPayload = [byte[]]@(
    [byte]$OperationCodes.GET,
    0, 0
)

Test-InvalidFrameRejected `
    "Empty key is rejected" `
    (New-Frame $EmptyKeyPayload)

# ------------------------------------------------------------
# Summary
# ------------------------------------------------------------

Write-Host ""
Write-Host "========================================"
Write-Host "Test summary"
Write-Host "========================================"
Write-Host "Passed: $script:Passed" -ForegroundColor Green
Write-Host "Failed: $script:Failed" -ForegroundColor Red
Write-Host "Total:  $($script:Passed + $script:Failed)"
Write-Host ""

if ($script:Failed -gt 0) {
    exit 1
}

Write-Host "ALL TESTS PASSED" -ForegroundColor Green
exit 0
