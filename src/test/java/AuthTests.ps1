param(
    [string]$HostName = "127.0.0.1",
    [int]$Port = 40001
)

$ErrorActionPreference = "Stop"
$Utf8 = [System.Text.Encoding]::UTF8

$AuthUserName = $env:ADMIN_USERNAME
$AuthPassword = $env:ADMIN_PASSWORD

if ([string]::IsNullOrWhiteSpace($AuthUserName)) {
    throw "Set CACHE_ADMIN_USERNAME before running this test."
}
if ([string]::IsNullOrEmpty($AuthPassword)) {
    throw "Set CACHE_ADMIN_PASSWORD before running this test."
}

[byte[]]$AuthUserNameBytes = $Utf8.GetBytes($AuthUserName)
[byte[]]$AuthPasswordBytes = $Utf8.GetBytes($AuthPassword)

if ($AuthUserNameBytes.Length -gt 1024 -or $AuthPasswordBytes.Length -gt 4096) {
    throw "Credentials exceed the authentication protocol limits."
}

$OperationCodes = @{
    SET = 1
    GET = 2
}

$DataTypeString = 1
$ResponseCreated = 3
$ResponseFound = 4

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
    [byte[]]$bytes = [BitConverter]::GetBytes($Number)
    if ([BitConverter]::IsLittleEndian) {
        [Array]::Reverse($bytes)
    }
    return ,$bytes
}

function Read-Exact {
    param(
        [System.IO.Stream]$Stream,
        [int]$Count
    )

    [byte[]]$buffer = [byte[]]::new($Count)
    $offset = 0

    while ($offset -lt $Count) {
        $read = $Stream.Read($buffer, $offset, $Count - $offset)
        if ($read -eq 0) {
            throw [System.IO.EndOfStreamException]::new(
                    "Peer closed the connection before the frame was complete."
            )
        }
        $offset += $read
    }

    return ,$buffer
}

function Read-Frame {
    param([System.IO.Stream]$Stream)

    [byte[]]$prefix = Read-Exact $Stream 4
    [long]$length = ([long]$prefix[0] * 16777216) +
            ([long]$prefix[1] * 65536) +
            ([long]$prefix[2] * 256) +
            [long]$prefix[3]

    if ($length -lt 1 -or $length -gt 1048576) {
        throw "Invalid frame length received: $length"
    }

    [byte[]]$payload = Read-Exact $Stream ([int]$length)
    return ,$payload
}

function New-Frame {
    param([byte[]]$Payload)

    $frame = [System.Collections.Generic.List[byte]]::new()
    $frame.AddRange((Get-UInt32BE $Payload.Length))
    $frame.AddRange($Payload)
    return ,$frame.ToArray()
}

function New-AuthFrame {
    $payload = [System.Collections.Generic.List[byte]]::new()
    $payload.AddRange((Get-UInt16BE $AuthUserNameBytes.Length))
    $payload.AddRange($AuthUserNameBytes)
    $payload.AddRange((Get-UInt16BE $AuthPasswordBytes.Length))
    $payload.AddRange($AuthPasswordBytes)

    return ,(New-Frame $payload.ToArray())
}

function New-CommandFrame {
    param(
        [ValidateSet("SET", "GET")]
        [string]$Operation,
        [string]$Key,
        [string]$Value
    )

    [byte[]]$keyBytes = $Utf8.GetBytes($Key)
    if ($keyBytes.Length -eq 0 -or $keyBytes.Length -gt 65535) {
        throw "Invalid key length."
    }

    $payload = [System.Collections.Generic.List[byte]]::new()
    $payload.Add([byte]$OperationCodes[$Operation])
    $payload.AddRange((Get-UInt16BE $keyBytes.Length))
    $payload.AddRange($keyBytes)

    if ($Operation -eq "SET") {
        [byte[]]$valueBytes = $Utf8.GetBytes($Value)
        $payload.Add([byte]$DataTypeString)
        $payload.AddRange((Get-UInt32BE $valueBytes.Length))
        $payload.AddRange($valueBytes)

        # Expiration is an absolute Unix epoch timestamp in milliseconds.
        # Keep the test key temporary so the test does not pollute the cache.
        [long]$expiresAt = [DateTimeOffset]::UtcNow.ToUnixTimeMilliseconds() + 60000
        $payload.AddRange((Get-Int64BE $expiresAt))
    }

    return ,(New-Frame $payload.ToArray())
}

function Open-TlsConnection {
    $tcp = [System.Net.Sockets.TcpClient]::new()
    $tcp.Connect($HostName, $Port)
    $networkStream = $tcp.GetStream()
    $networkStream.ReadTimeout = 5000
    $networkStream.WriteTimeout = 5000

    $tls = [System.Net.Security.SslStream]::new($networkStream, $false)
    $tls.ReadTimeout = 5000
    $tls.WriteTimeout = 5000
    $tls.AuthenticateAsClient($HostName)

    return @{
        Tcp = $tcp
        Tls = $tls
    }
}

function Close-TlsConnection {
    param($Connection)

    if ($null -ne $Connection) {
        if ($null -ne $Connection.Tls) {
            $Connection.Tls.Dispose()
        }
        if ($null -ne $Connection.Tcp) {
            $Connection.Tcp.Dispose()
        }
    }
}

function Send-Frame {
    param(
        [System.IO.Stream]$Stream,
        [byte[]]$Frame
    )
    $Stream.Write($Frame, 0, $Frame.Length)
    $Stream.Flush()
}

function Assert-CommandResponse {
    param(
        [byte[]]$Payload,
        [int]$ExpectedCode,
        [string]$ExpectedKey,
        [string]$ExpectedValue = $null
    )

    if ($Payload.Length -lt 3) {
        throw "Command response payload is too short."
    }

    $code = [int]$Payload[0]
    $keyLength = ([int]$Payload[1] * 256) + [int]$Payload[2]

    if ($keyLength -le 0 -or (3 + $keyLength) -gt $Payload.Length) {
        throw "Invalid key length in command response."
    }

    [byte[]]$keyBytes = [byte[]]::new($keyLength)
    [Array]::Copy($Payload, 3, $keyBytes, 0, $keyLength)
    $key = $Utf8.GetString($keyBytes)

    if ($code -ne $ExpectedCode) {
        throw "Expected response code $ExpectedCode, received $code."
    }
    if ($key -cne $ExpectedKey) {
        throw "Expected response key '$ExpectedKey', received '$key'."
    }

    if ($ExpectedCode -eq $ResponseFound) {
        $typeOffset = 3 + $keyLength
        if ($Payload.Length -lt ($typeOffset + 5)) {
            throw "FOUND response is truncated."
        }

        $typeCode = [int]$Payload[$typeOffset]
        $valueLengthOffset = $typeOffset + 1
        [long]$valueLength =
        ([long]$Payload[$valueLengthOffset] * 16777216) +
                ([long]$Payload[$valueLengthOffset + 1] * 65536) +
                ([long]$Payload[$valueLengthOffset + 2] * 256) +
                [long]$Payload[$valueLengthOffset + 3]

        $valueOffset = $valueLengthOffset + 4
        if ($typeCode -ne $DataTypeString -or
                $valueLength -ne ($Payload.Length - $valueOffset)) {
            throw "Unexpected FOUND response type or value length."
        }

        [byte[]]$valueBytes = [byte[]]::new([int]$valueLength)
        if ($valueLength -gt 0) {
            [Array]::Copy($Payload, $valueOffset, $valueBytes, 0, [int]$valueLength)
        }

        $value = $Utf8.GetString($valueBytes)
        if ($value -cne $ExpectedValue) {
            throw "Expected value '$ExpectedValue', received '$value'."
        }
    }
}

$testId = [Guid]::NewGuid().ToString("N").Substring(0, 12)
$key = "auth-session-test-$testId"
$value = "authenticated-session-ok-$testId"

Write-Host "Target: ${HostName}:${Port}"
Write-Host "`nTEST 1: Authenticated connection supports multiple commands"

$authenticatedConnection = $null
try {
    $authenticatedConnection = Open-TlsConnection

    # Authenticate once on this TLS connection.
    Send-Frame $authenticatedConnection.Tls (New-AuthFrame)
    [byte[]]$authResponse = Read-Frame $authenticatedConnection.Tls

    if ($authResponse.Length -ne 1 -or [int]$authResponse[0] -ne 1) {
        throw "Authentication failed or returned an invalid response."
    }
    Write-Host "  PASS: Authentication succeeded."

    # Send SET on the same connection.
    Send-Frame $authenticatedConnection.Tls (New-CommandFrame "SET" $key $value)
    [byte[]]$setResponse = Read-Frame $authenticatedConnection.Tls
    Assert-CommandResponse $setResponse $ResponseCreated $key
    Write-Host "  PASS: SET succeeded on the authenticated connection."

    # Send GET on the very same connection, without authenticating again.
    Send-Frame $authenticatedConnection.Tls (New-CommandFrame "GET" $key "")
    [byte[]]$getResponse = Read-Frame $authenticatedConnection.Tls
    Assert-CommandResponse $getResponse $ResponseFound $key $value
    Write-Host "  PASS: GET succeeded on the same connection without re-authentication."
}
finally {
    Close-TlsConnection $authenticatedConnection
}

Write-Host "`nTEST 2: Unauthenticated connections cannot execute commands"

# Test both GET and SET. Each attempt uses a fresh connection and deliberately
# skips AUTH. The server is expected to close/reject the connection before
# returning a normal cache-command response.
$unauthenticatedOperations = @(
    @{ Operation = "GET"; Key = $key; Value = "" },
    @{ Operation = "SET"; Key = "$key-unauth"; Value = "must-not-be-stored" }
)

foreach ($testCase in $unauthenticatedOperations) {
    $unauthenticatedConnection = $null

    try {
        $unauthenticatedConnection = Open-TlsConnection

        Send-Frame $unauthenticatedConnection.Tls (
        New-CommandFrame $testCase.Operation $testCase.Key $testCase.Value
        )

        try {
            [byte[]]$unexpectedResponse = Read-Frame $unauthenticatedConnection.Tls

            if ($unexpectedResponse.Length -ge 3) {
                $responseKeyLength = ([int]$unexpectedResponse[1] * 256) +
                        [int]$unexpectedResponse[2]

                if ($responseKeyLength -gt 0 -and
                        (3 + $responseKeyLength) -le $unexpectedResponse.Length) {
                    [byte[]]$responseKeyBytes = [byte[]]::new($responseKeyLength)
                    [Array]::Copy(
                            $unexpectedResponse, 3,
                            $responseKeyBytes, 0, $responseKeyLength
                    )
                    $responseKey = $Utf8.GetString($responseKeyBytes)

                    if ($responseKey -ceq $testCase.Key) {
                        throw "FAIL: Server returned a cache response for unauthenticated $($testCase.Operation)."
                    }
                }
            }

            throw "FAIL: Server sent a response instead of rejecting unauthenticated $($testCase.Operation)."
        }
        catch [System.IO.EndOfStreamException] {
            Write-Host "  PASS: Unauthenticated $($testCase.Operation) was rejected."
        }
        catch [System.IO.IOException] {
            # A timeout is not evidence of rejection; the server must actively
            # close/reject the unauthenticated connection.
            if ($_.Exception.ToString() -match '(?i)timed?\s*out|timeout') {
                throw "Server did not reject unauthenticated $($testCase.Operation) before the read timed out."
            }

            Write-Host "  PASS: Unauthenticated $($testCase.Operation) was rejected/connection closed."
        }
        catch [System.Net.Sockets.SocketException] {
            if ($_.Exception.SocketErrorCode -eq
                    [System.Net.Sockets.SocketError]::TimedOut) {
                throw "Server did not reject unauthenticated $($testCase.Operation) before the read timed out."
            }

            Write-Host "  PASS: Unauthenticated $($testCase.Operation) was rejected/connection reset."
        }
    }
    finally {
        Close-TlsConnection $unauthenticatedConnection
    }
}

Write-Host "`nAll authentication session tests passed." -ForegroundColor Green
