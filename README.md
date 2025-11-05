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

- GraalVM JDK 11 or later
- Kotlin 1.9+
- Maven (for building)

## Building

This project uses [Maven](https://maven.apache.org/) for build management.

To build and run:

```bash
./mvnw package
java -jar target/browser-from-scratch-1.0-SNAPSHOT.jar https://example.com
```

To run tests:

```bash
./mvnw test
```

## Usage

Run the browser with a URL as an argument:

```bash
java -jar target/browser-from-scratch-1.0-SNAPSHOT.jar https://example.com
```

Or load a local file:

```bash
java -jar target/browser-from-scratch-1.0-SNAPSHOT.jar file:///path/to/your/file.html
```

For data URLs:

```bash
java -jar target/browser-from-scratch-1.0-SNAPSHOT.jar "data:text/html,<html><body>Hello World</body></html>"
```

To view source:

```bash
java -jar target/browser-from-scratch-1.0-SNAPSHOT.jar view-source:https://example.com
```

## Project Structure

- `src/io/github/mpichler94/`
  - `browser/`: Browser implementation
    - `layout/`: HTML layout processing
    - `main.kt`: Entry point
    - `Browser.kt`: Main browser logic
    - `Chrome.kt`: Browser chrome (address bar, tabs, etc.)
    - `CSSParser.kt`: CSS parser
    - `Drawable.kt`: Everything that is drawn by the browser
    - `HTMLParser.kt`: HTML parser
    - `HttpClient.kt`: HTTP client implementation
    - `Request.kt`: HTTP request handling
    - `Response.kt`: HTTP response handling
    - `Tab.kt`: Browser tab (handles web page logic)
    - `URL.kt`: URL parsing utilities
  - `server/`: Small HTTP server for testing

## Dependencies

- Kotlin Standard Library
- Kotlinx Coroutines
- Kotlin Logging with Logback
- AssertJ (for testing)
- MockServer (for testing)
- GraalVM Polyglot (for running JavaScript)

## License

This project is open source under the MIT license. Please check the LICENSE.txt file for details.

## Acknowledgments

Inspired by [browser.engineering](https://browser.engineering) by Pavel Panchekha and James R. Wilcox.

## Implemented Exercises

- [x] 1-1: Http/1.1
- [x] 1-2: File URLs
- [x] 1-3: Data URLs
- [x] 1-4: Entities
- [x] 1-5: view-source
- [x] 1-6: Keep-alive
- [x] 1-7: Redirects
- [x] 1-8: Caching
- [x] 2-1: Line breaks
- [x] 2-2: Mouse wheel
- [x] 2-3: Resizing
- [x] 2-4: Scrollbar
- [ ] 2-5: Emoji
- [x] 2-6: about:blank
- [ ] 2-7: Alternate text direction
- [ ] 3-1: Centered text
- [x] 3-2: Superscripts
- [ ] 3-3: Soft hyphens
- [x] 3-4: Small caps
- [x] 3-5: Preformatted text
- [x] 4-1: Comments
- [x] 4-2: Paragraphs
- [x] 4-3: Scripts
- [x] 4-4: Quoted attributes
- [ ] 4-5: Syntax highlighting
- [ ] 4-6: Mis-nested formatting tags
- [x] 5-1: Links bar
- [x] 5-2: Hidden head
- [x] 5-3: Bullets
- [x] 5-4: Table of contents
- [x] 5-5: Anonymous block boxes
- [ ] 5-6: Run-ins
- [x] 6-1: Fonts
- [ ] 6-2: Width/height
- [x] 6-3: Class selectors
- [x] 6-4: display
- [ ] 6-5: Shorthand properties
- [ ] 6-6: Inline style sheets
- [ ] 6-7: Fast descendant selectors
- [ ] 6-8: Selector sequences
- [ ] 6-9: !important
- [x] 7-1: Backspace
- [x] 7-2: Middle-click
- [x] 7-3: Window title
- [x] 7-4: Forward
- [x] 7-5: Fragments
- [ ] 7-6: Search
- [ ] 7-7: Visited links
- [ ] 7-8: Bookmarks
- [x] 7-9: Cursor
- [ ] 7-10: Multiple windows
- [ ] 7-11: Clicks via the display list
- [x] 8-1: Enter key
- [ ] 8-2: GET forms
- [x] 8-3: Blurring
- [ ] 8-4: Check boxes
- [ ] 8-5: Resubmit forms
- [ ] 8-6: Message board
- [ ] 8-7: Persistence
- [ ] 8-8: Rich buttons
- [ ] 8-9: HTML chrome
- [x] 9-1: Node.children
- [x] 9-2: createElement
- [x] 9-3: removeChild
- [x] 9-4: IDs
- [x] 9-5: Event bubbling
- [x] 9-6: Serializing HTML
- [ ] 9-7: Script-added scripts and style sheets