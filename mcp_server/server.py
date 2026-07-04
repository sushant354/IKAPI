import os
from typing import Optional
from dotenv import load_dotenv
import httpx
from mcp.server.fastmcp import FastMCP

# Load environment variables
load_dotenv()

API_TOKEN = os.getenv("INDIANKANOON_API_TOKEN")
BASE_URL = "https://api.indiankanoon.org"

if not API_TOKEN:
    raise ValueError("INDIANKANOON_API_TOKEN is not set in the environment or .env file")

# Initialize FastMCP Server
mcp = FastMCP("IndianKanoon")

# Setup Authorization headers
headers = {
    "Authorization": f"Token {API_TOKEN}",
    "Accept": "application/json"
}

@mcp.tool()
async def search_kanoon(query: str, page_num: int = 0, max_pages: int = 1, max_cites: Optional[int] = None) -> dict:
    """
    Search the Indian Kanoon database for legal documents.
    
    Args:
        query: The search query string (supports operators like ANDD, ORR, NOTT, and filters like doctypes:supremecourt, title:kesavananda, cite:citation).
        page_num: The starting page number of the search results (0-indexed).
        max_pages: Max pages of search results to return (up to 1000).
        max_cites: Optional list size for citations per matching document (capped at 50).
    """
    url = f"{BASE_URL}/search/"
    params = {
        "formInput": query,
        "pagenum": page_num,
        "maxpages": max_pages
    }
    if max_cites is not None:
        params["maxcites"] = max_cites
        
    async with httpx.AsyncClient() as client:
        response = await client.post(url, headers=headers, params=params)
        response.raise_for_status()
        return response.json()

@mcp.tool()
async def get_document(doc_id: int, max_cites: int = 0, max_citedby: int = 0) -> dict:
    """
    Retrieve the text content and metadata of a legal document by its ID.
    
    Args:
        doc_id: Unique ID of the document (tid).
        max_cites: Maximum number of citations to retrieve.
        max_citedby: Maximum number of citedby references to retrieve.
    """
    url = f"{BASE_URL}/doc/{doc_id}/"
    params = {}
    if max_cites > 0:
        params["maxcites"] = max_cites
    if max_citedby > 0:
        params["maxcitedby"] = max_citedby
        
    async with httpx.AsyncClient() as client:
        response = await client.post(url, headers=headers, params=params)
        response.raise_for_status()
        return response.json()

@mcp.tool()
async def get_document_fragment(doc_id: int, query: str) -> dict:
    """
    Retrieve specific context fragments/snippets matching a query within a document.
    
    Args:
        doc_id: Unique ID of the document (tid).
        query: The search query to match fragments against.
    """
    url = f"{BASE_URL}/docfragment/{doc_id}/"
    params = {
        "formInput": query
    }
    async with httpx.AsyncClient() as client:
        response = await client.post(url, headers=headers, params=params)
        response.raise_for_status()
        return response.json()

@mcp.tool()
async def get_original_document(doc_id: int) -> dict:
    """
    Retrieve the original document copy (Base64-encoded PDF/text) if available.
    
    Args:
        doc_id: Unique ID of the document (tid).
    """
    url = f"{BASE_URL}/origdoc/{doc_id}/"
    async with httpx.AsyncClient() as client:
        response = await client.post(url, headers=headers)
        response.raise_for_status()
        return response.json()

def main():
    mcp.run()

if __name__ == "__main__":
    main()
