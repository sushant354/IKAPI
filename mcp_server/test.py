import asyncio
import os
from server import search_kanoon, get_document, get_document_fragment, get_original_document

async def run_tests():
    print("--- Starting Indian Kanoon MCP Tools Integration Tests ---")
    
    # 1. Test Search
    query = "theft or robbery or dacoity"
    print(f"\n[Test 1] Searching for: '{query}'...")
    try:
        search_res = await search_kanoon(query=query, page_num=0, max_pages=1)
        num_docs = len(search_res.get("docs", []))
        print(f"Success! Found {num_docs} documents on page 0.")
        
        if num_docs > 0:
            sample_doc = search_res["docs"][0]
            doc_id = sample_doc.get("tid")
            title = sample_doc.get("title")
            print(f"Sample Document: ID={doc_id}, Title='{title}'")
            
            # 2. Test Fetch Document
            print(f"\n[Test 2] Fetching document details for ID {doc_id}...")
            doc_res = await get_document(doc_id=doc_id)
            print(f"Success! Document title: '{doc_res.get('title')}'")
            
            # 3. Test Fetch Fragment
            print(f"\n[Test 3] Fetching document fragment for ID {doc_id} with query '{query}'...")
            frag_res = await get_document_fragment(doc_id=doc_id, query=query)
            print("Success! Fragment response snippet:", str(frag_res)[:200] + "...")
            
            # 4. Test Fetch Original Copy
            print(f"\n[Test 4] Fetching original document copy for ID {doc_id}...")
            try:
                orig_res = await get_original_document(doc_id=doc_id)
                if "doc" in orig_res:
                    print(f"Success! Found original document. Content-Type: {orig_res.get('Content-Type')}")
                elif "errmsg" in orig_res:
                    print(f"API returned message (expected if no PDF copy is available): '{orig_res.get('errmsg')}'")
                else:
                    print("API returned response:", orig_res)
            except Exception as e:
                print(f"Original doc fetch returned exception (expected if not available): {e}")
                
        else:
            print("No documents found to perform dependent tests.")
            
    except Exception as e:
        print(f"Test failed with exception: {e}")

if __name__ == "__main__":
    asyncio.run(run_tests())
