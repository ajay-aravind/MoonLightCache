# MoonLight Cache 🌙

**A lightweight, high-performance in-memory cache server built from scratch in Java.**

MoonLight Cache is an experimental in-memory key-value cache server designed to explore the engineering behind modern caching systems. It provides a custom binary communication protocol, persistent TCP connections, typed values, expiration support, and TLS-secured client communication.

The project focuses on understanding how systems such as Redis work internally — from binary protocol design and network communication to concurrent data access and memory management.

> **Status:** Under active development. APIs and the wire protocol may change.

## ✨ Features

### Core Cache
- In-memory key-value storage.
- Support for multiple data types:
  - String
  - Integer
  - Long
  - Boolean
  - Character
- Key expiration using TTL.
- Basic cache operations:
  - `SET` – Store a value.
  - `GET` – Retrieve a value.
  - `EXISTS` – Check whether a key exists.
  - `DELETE` – Remove a key.

### Networking and Protocol
- Custom binary request/response protocol.
- TCP-based client-server communication.
- Persistent connections supporting multiple requests.
- Length-prefixed messages using big-endian byte ordering.
- UTF-8 encoding for keys and strings.
- Protocol-level validation for malformed and oversized frames.

### Security
- TLS-encrypted client-server communication.
- Server certificate validation.
- Username/password authentication.
- Authentication required before cache operations.
- Authentication scoped to individual client connections.

### Concurrency
- Concurrent client connections.
- Per-key synchronization to support concurrent operations on different keys.
- Background cache maintenance and expiration handling.

## 🏗️ Architecture

MoonLight Cache follows a client-server architecture.

```text
                Client Applications
                        |
                        |
                  TCP / TLS
                        |
                        v
              +-------------------+
              |    Cache Server   |
              +-------------------+
                        |
              +-------------------+
              | Connection Handler|
              +-------------------+
                        |
              +-------------------+
              | Authentication    |
              +-------------------+
                        |
              +-------------------+
              | Request Parser    |
              +-------------------+
                        |
              +-------------------+
              | Command Processor |
              +-------------------+
                        |
              +-------------------+
              |  Storage Engine   |
              +-------------------+
                        |
              +-------------------+
              | In-Memory Storage |
              +-------------------+
```

The server accepts client connections, establishes TLS, authenticates the client, and then processes cache commands over the same connection.

Requests and responses are encoded using a custom binary protocol to avoid the overhead of text-based serialization.

## 📡 Binary Protocol

MoonLight Cache uses a length-prefixed binary protocol.

### Request format

Every request begins with a 4-byte big-endian frame length, followed by the request payload.

```text
+---------------------------+
| Frame Length (4 bytes)    |
+---------------------------+
| Operation (1 byte)        |
+---------------------------+
| Key Length (2 bytes)      |
+---------------------------+
| Key (UTF-8)               |
+---------------------------+
| Data Type (1 byte)        |  SET only
+---------------------------+
| Value Length (4 bytes)    |  SET only
+---------------------------+
| Value (variable)          |  SET only
+---------------------------+
| TTL (8 bytes)             |  SET only
+---------------------------+
```

### Supported operations

| Operation | Description |
|---|---|
| SET | Store a key-value pair |
| GET | Retrieve a value |
| EXISTS | Check key existence |
| DELETE | Delete a key |

### Authentication

Authentication is performed immediately after the TLS handshake and before cache commands are accepted.

The authentication request contains:

```text
+---------------------------+
| Frame Length (4 bytes)    |
+---------------------------+
| Username Length (2 bytes) |
+---------------------------+
| Username (UTF-8)          |
+---------------------------+
| Password Length (2 bytes) |
+---------------------------+
| Password (UTF-8)          |
+---------------------------+
```

The server responds with an authentication status indicating success or failure.

Once authenticated, the client can send multiple cache requests over the same connection without authenticating again.

## 🚀 Getting Started

### Prerequisites

- Java 21 or later.
- PowerShell (for the provided Windows test client).
- A Java build environment configured for the project.

### Clone the repository

```bash
git clone <YOUR_REPOSITORY_URL>
cd MoonLightCache
```

### Configure authentication

Set the administrator credentials before starting the server.

PowerShell:

```powershell
$env:CACHE_ADMIN_USERNAME = "admin"
$env:CACHE_ADMIN_PASSWORD = "your-strong-password"
```

### Configure TLS

MoonLight Cache uses a TLS certificate and private key stored in a PKCS12 keystore.

You can generate a development certificate using `keytool`:

```powershell
keytool -genkeypair `
  -alias cache-server `
  -keyalg RSA `
  -keysize 3072 `
  -storetype PKCS12 `
  -keystore server-keystore.p12 `
  -validity 365 `
  -dname "CN=localhost" `
  -ext "SAN=dns:localhost,ip:127.0.0.1"
```

Configure the keystore environment variables:

```powershell
$env:CACHE_TLS_KEYSTORE = "C:\path\to\server-keystore.p12"
$env:CACHE_TLS_KEYSTORE_PASSWORD = "your-keystore-password"
```

Make sure the client trusts the server certificate. Do not disable certificate validation.

### Start the server

Build and launch the server using the build configuration included in the repository.

## 💻 Client Usage

The repository includes a PowerShell client for communicating with the cache server.

Set credentials:

```powershell
$env:CACHE_ADMIN_USERNAME = "admin"
$env:CACHE_ADMIN_PASSWORD = "your-strong-password"
```

Examples:

**Store a string**

```powershell
.\TestMoonLightCacheServer.ps1 SET greeting "Hello MoonLight" -1 stringType
```

**Retrieve a value**

```powershell
.\TestMoonLightCacheServer.ps1 GET greeting
```

**Check whether a key exists**

```powershell
.\TestMoonLightCacheServer.ps1 EXISTS greeting
```

**Delete a key**

```powershell
.\TestMoonLightCacheServer.ps1 DELETE greeting
```

The client establishes a TLS connection, authenticates, executes the requested operation, and closes the connection.

## 🧪 Testing

Correctness under concurrent access and network failures is an important part of this project.

The project includes or is developing tests for:

- Cache operations and value types.
- Concurrent access to cache entries.
- Concurrent SET, GET, and DELETE operations.
- Request and response serialization.
- Binary protocol validation.
- Authentication request and response parsing.
- Authenticated and unauthenticated connection behavior.
- Persistent connection handling.

The PowerShell authentication integration test checks that:
1. Multiple cache commands execute successfully on one authenticated connection.
2. Cache commands are rejected on unauthenticated connections.

Run the Java unit tests using the build system configured in the repository.

## 🛠️ Technology Stack

- **Language:** Java
- **Networking:** TCP / Java Sockets
- **Transport Security:** TLS / SSL
- **Serialization:** Custom binary protocol
- **Encoding:** UTF-8
- **Client:** PowerShell
- **Testing:** JUnit 5

## 🗺️ Roadmap

Some areas I'd like to explore as the project evolves:

- [ ] Metrics and monitoring.
- [ ] Memory usage limits.
- [ ] Eviction policies (LRU / LFU).
- [ ] Atomic cache operations.
- [ ] Batch operations and request pipelining.
- [ ] Improved client libraries.
- [ ] Persistence using a write-ahead log.
- [ ] Cache replication.
- [ ] Performance benchmarking and profiling.

## 🎯 Project Goals

MoonLight Cache is not just about implementing a key-value store.

The goal is to explore the engineering problems involved in building reliable backend infrastructure:

- How do network protocols work internally?
- How should a server handle thousands of concurrent connections?
- How do we guarantee correctness under concurrent operations?
- How should memory be managed efficiently?
- How do we design protocols that are compact, extensible, and robust?
- How should a server handle malformed requests and unexpected client disconnections?

These questions drive the design and development of MoonLight Cache.

## 🤝 Contributions

Suggestions, discussions, bug reports, and contributions are welcome.

If you're interested in backend engineering, distributed systems, networking, or storage engines, feel free to explore the code and contribute.
