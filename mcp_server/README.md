# Indian Kanoon MCP Server

An official Model Context Protocol (MCP) server for the Indian Kanoon API, built using the Python **FastMCP** framework. 

This server allows LLMs (like Claude, Cursor, ChatGPT, etc.) to query the Indian Kanoon legal database directly, search for court judgments, download document texts, view citations, and retrieve document fragments.

---

## Prerequisites & Installation

The server is built to run seamlessly with **`uv`**, Astral's fast Python package installer and runner.

### 1. Install `uv`
If you do not have `uv` installed, you can install it using one of the following methods:

**macOS (Homebrew):**
```bash
brew install uv
```

**Linux/macOS (curl):**
```bash
curl -LsSf https://astral.sh/uv/install.sh | sh
```

**Windows:**
```powershell
powershell -c "irm https://astral.sh/uv/install.ps1 | iex"
```

### 2. Install Project Dependencies
Navigate to the `mcp_server` directory and synchronize the dependencies:
```bash
uv sync
```

### 3. Configure Your API Token
Create a `.env` file in the `mcp_server` directory and add your Indian Kanoon API token:

```env
INDIANKANOON_API_TOKEN=your_actual_api_token_here
```

---

## How to Run & Test the Server

Because this is a standard input/output (stdio) based MCP server, running it directly as a script (e.g. `uv run server.py`) will wait for JSON-RPC messages and throw a validation error if run interactively in a normal shell. Use the following methods to test and run it correctly:

### 1. Run the Integration Tests
We have provided a test script to verify that your configuration and API token are working correctly:
```bash
uv run python test.py
```
This runs a search query and fetches document fragments directly from the API.

### 2. Start the Interactive MCP Inspector
FastMCP comes with a developer console/UI that lets you inspect and trigger the server's tools in your web browser:
```bash
uv run mcp dev server.py
```
This will start the server and print a local URL (e.g., `http://localhost:5173` or `http://127.0.0.1:6274`). Open that URL in your browser to view the interactive console.

*Alternatively, you can manually launch the official MCP Inspector using `npx`:*
```bash
npx @modelcontextprotocol/inspector uv run server.py
```

---

## Connecting the Server to MCP Clients

### 1. Claude Desktop App
To connect the Indian Kanoon MCP server to Claude, open your Claude configuration file:
* **macOS:** `~/Library/Application Support/Claude/claude_desktop_config.json`
* **Windows:** `%APPDATA%\Claude\claude_desktop_config.json`

Add the server to the `mcpServers` configuration:

```json
{
  "mcpServers": {
    "indian-kanoon": {
      "command": "uv",
      "args": [
        "run",
        "--project",
        "/absolute/path/to/your/workspace/mcp_server",
        "/absolute/path/to/your/workspace/mcp_server/server.py"
      ],
      "env": {
        "INDIANKANOON_API_TOKEN": "your_actual_api_token_here"
      }
    }
  }
}
```
*(Make sure to replace `/absolute/path/to/your/workspace/mcp_server` with the actual absolute path to your project folder).*

### 2. Cursor IDE
To add the server in Cursor:
1. Open Cursor Settings -> **Features** -> **MCP**.
2. Click **+ Add New MCP Server**.
3. Fill in:
   * **Name**: `indian-kanoon`
   * **Type**: `stdio`
   * **Command**: `uv run --project /absolute/path/to/your/workspace/mcp_server /absolute/path/to/your/workspace/mcp_server/server.py`

---

## Exposed Tools

The server registers four main tools with the LLM:

### 1. `search_kanoon`
Search the Indian Kanoon legal database for documents.
* **Arguments:**
  * `query` (string, required): The search query. Supports logical operators (`ANDD`, `ORR`, `NOTT`), phrase queries (in quotation marks), and metadata prefixes:
    * `doctypes:supremecourt` (or `delhi`, `bombay`, etc.)
    * `fromdate:DD-MM-YYYY` / `todate:DD-MM-YYYY`
    * `title:kesavananda`
    * `cite:citation`
    * `author:arijit pasayat`
    * `bench:arijit pasayat`
  * `page_num` (integer, optional, default: `0`): Page index.
  * `max_pages` (integer, optional, default: `1`): Maximum pages to retrieve.
  * `max_cites` (integer, optional): Optional list size for citations per matching document (capped at 50).

### 2. `get_document`
Retrieve the full text, metadata, and citation listings of a specific document.
* **Arguments:**
  * `doc_id` (integer, required): Unique document ID (`tid`).
  * `max_cites` (integer, optional, default: `0`): Maximum citations to retrieve in `citeList` (up to 50).
  * `max_citedby` (integer, optional, default: `0`): Maximum references citing this document in `citedbyList` (up to 50).

### 3. `get_document_fragment`
Retrieve highlighted text fragments/snippets matching a query within a specific document.
* **Arguments:**
  * `doc_id` (integer, required): Unique document ID (`tid`).
  * `query` (string, required): Search term or phrase to find fragments of.

### 4. `get_original_document`
Retrieve the original PDF/text copy if available (Base64-encoded PDF).
* **Arguments:**
  * `doc_id` (integer, required): Unique document ID (`tid`).
