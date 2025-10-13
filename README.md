# Browser from Scratch

A minimal web browser implementation written in Kotlin, following the principles
from [browser.engineering](https://browser.engineering).

## Overview

This project implements a basic web browser from scratch, focusing on core web technologies including URL parsing, HTTP
requests, and simple HTML rendering. It's designed as an educational tool to understand how browsers work under the
hood.

## Features

- **URL Parsing**: Supports HTTP, HTTPS, file://, and data:// schemes
- **HTTP Client**: Custom HTTP client implementation with basic request/response handling
- **HTML Rendering**: Simple text-based HTML renderer that strips tags and handles basic entities
- **Source View**: Support for `view-source:` URLs to display raw HTML
- **File Support**: Can load local HTML files

## Requirements

- JDK 11 or later
- Kotlin 1.9+
- Amper (for building)

## Building

This project uses [Amper](https://github.com/JetBrains/Amper) for build management.

To build and run:

```bash
./amper run
```

To run tests:

```bash
./amper test
```

## Usage

Run the browser with a URL as an argument:

```bash
./amper run -- https://example.com
```

Or load a local file:

```bash
./amper run -- file:///path/to/your/file.html
```

For data URLs:

```bash
./amper run -- "data:text/html,<html><body>Hello World</body></html>"
```

To view source:

```bash
./amper run -- view-source:https://example.com
```

## Project Structure

- `src/io/github/mpichler94/browser/`: Main source code
    - `main.kt`: Entry point
    - `Browser.kt`: Main browser logic
    - `HttpClient.kt`: HTTP client implementation
    - `URL.kt`: URL parsing utilities
    - `Request.kt`: HTTP request handling
    - `Response.kt`: HTTP response handling
- `test/`: Unit tests
- `testResources/`: Test HTML files

## Dependencies

- Kotlin Standard Library
- Kotlinx Coroutines
- Kotlin Logging with Logback
- AssertJ (for testing)
- MockServer (for testing)

## License

This project is open source under the MIT license. Please check the LICENSE.txt file for details.

## Acknowledgments

Inspired by [browser.engineering](https://browser.engineering) by Pavel Panchekha and James R. Wilcox.