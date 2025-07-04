import argparse
import logging
import os
import re
import codecs
import json
import http.client
import urllib.request, urllib.parse, urllib.error
import base64
import glob
import csv
import datetime
import time
import multiprocessing
from bs4 import BeautifulSoup

def print_usage(progname):
    print ('''python %s -t token -o offset -n limit -d datadir''' % progname)

class IKApi:
    def __init__(self, args, storage):
        self.logger     = logging.getLogger('ikapi')

        self.headers    = {'Authorization': 'Token %s' % args.token, \
                           'Accept': 'application/json'}

        self.basehost   = 'api.indiankanoon.org'
        self.storage    = storage
        self.maxcites   = args.maxcites
        self.maxcitedby = args.maxcitedby
        self.orig       = args.orig
        self.maxpages   = args.maxpages
        self.pathbysrc  = args.pathbysrc
        self.queue      = multiprocessing.Queue(20)
        self.num_workers= args.numworkers 
        self.addedtoday = args.addedtoday
        self.fromdate   = args.fromdate
        self.todate     = args.todate
        self.sortby     = args.sortby
        self.csv_output = args.csv_output
        self.docs_count  = args.docs_count

        if self.maxpages > 100:
            self.maxpages = 100

    def call_api_direct(self, url):
        connection = http.client.HTTPSConnection(self.basehost)
        connection.request('POST', url, headers = self.headers)
        response = connection.getresponse()
        results = response.read()

        if isinstance(results, bytes):
            results = results.decode('utf8')
        return results 
   
    def call_api(self, url):
        count = 0

        while count < 3:
            try:
                results = self.call_api_direct(url)
            except Exception as e:
                self.logger.warning('Error in call_api %s %s', url, e)
                count += 1
                time.sleep(count * 10)
                continue

            if results == None or (isinstance(results, str) and \
                                   re.match('error code:', results)):
                self.logger.warning('Error in call_api %s %s', url, results)
                count += 1
                time.sleep(count * 10)
            else:
                break 

        return results

    def fetch_doc(self, docid):
        url = '/doc/%d/' % docid

        args = []
        if self.maxcites > 0:
            args.append('maxcites=%d' % self.maxcites)

        if self.maxcitedby > 0:
            args.append('maxcitedby=%d' % self.maxcitedby)

        if args:
            url = url + '?' + '&'.join(args)

        return self.call_api(url)

    def fetch_docmeta(self, docid):
        url = '/docmeta/%d/' % docid

        args = []
        if self.maxcites != 0:
            args.append('maxcites=%d' % self.maxcites)

        if self.maxcitedby != 0:
            args.append('maxcitedby=%d' % self.maxcitedby)

        if args:
            url = url + '?' + '&'.join(args)

        return self.call_api(url)

    def fetch_orig_doc(self, docid):
        url = '/origdoc/%d/' % docid
        return self.call_api(url)

    def fetch_doc_fragment(self, docid, q):
        q   = urllib.parse.quote_plus(q.encode('utf8'))
        url = '/docfragment/%d/?formInput=%s' % (docid,  q)
        return self.call_api(url)

    def search(self, q, pagenum, maxpages):
        q = urllib.parse.quote_plus(q.encode('utf8'))
        url = '/search/?formInput=%s&pagenum=%d&maxpages=%d' % (q, pagenum, maxpages)
        return self.call_api(url)
    
    def fetch_citedby_docs(self,docid,log_stmt=""):
        q="citedby:%d"%(docid)
        return self.save_search_results(q,log_stmt)

    def save_doc_fragment(self, docid, q):
        success = False

        jsonstr = self.fetch_doc_fragment(docid, q)
        if not jsonstr:
            return False

        jsonpath = self.storage.get_json_path('%d q: %s' % (docid, q))
        success = self.storage.save_json(jsonstr, jsonpath)
        return success    

    def download_doc(self, docid, docpath):    
        success = False
        orig_needed = self.orig
        jsonpath, origpath = self.storage.get_json_orig_path(docpath, docid)

        if not self.storage.exists(jsonpath):
            jsonstr = self.fetch_doc(docid)

            try:
                d = json.loads(jsonstr)
            except Exception as e:
                self.logger.error('Error in getting doc %s %s', docid, e)
                return success

            if 'errmsg' in d:
                self.logger.error('Error in getting doc %s', docid)
                return success
        
            self.logger.info('Saved %s', d['title'])
            self.storage.save_json(jsonstr, jsonpath)
            success = True

            if orig_needed:
                if not d['courtcopy']:
                    orig_needed = False

        if orig_needed and not self.storage.exists_original(origpath):
            orig = self.fetch_orig_doc(docid)
            if orig and self.storage.save_original(orig, origpath):
                self.logger.info('Saved original %s', docid)
        return success        

    def make_query(self, q):
        if self.fromdate:
            q += ' fromdate: %s' % self.fromdate

        if self.todate:
            q += ' todate: %s' % self.todate

        if self.addedtoday:
            q += ' added:today'

        if self.sortby:
            q += ' sortby: ' + self.sortby

        return q

    def download_doctype(self, doctype):
        q = 'doctypes: %s' % doctype
        q = self.make_query(q)
        return self.save_search_results(q)
    
    def save_search_results(self, q,log_stmt=""):
        if not self.docs_count and (not self.pathbysrc or self.csv_output):
            datadir = self.storage.get_search_path(q)
        
        if not self.docs_count and self.csv_output:
            tochandle, tocwriter = self.storage.get_tocwriter(datadir)

        pagenum = 0
        current = 1
        unique_docs = set()
        while 1:
            results = self.search(q, pagenum, self.maxpages)
            obj = json.loads(results)

            if 'errmsg' in obj:
                self.logger.warning('Error: %s, pagenum: %d q: %s', obj['errmsg'], pagenum, q)
                break

            if 'docs' not in obj or len(obj['docs']) <= 0:
                break
            docs = obj['docs']
            if len(docs) <= 0:
                break
            
            self.logger.warning('Num results: %d, pagenum: %d found: %s q: %s', len(docs), pagenum, obj['found'], q)
            for doc in docs:
                docid   = doc['tid']
                title   = doc['title']

                if  not self.docs_count and self.csv_output:
                    toc = {'docid': docid, 'title': title, 'position': current, \
                        'date': doc['publishdate'], 'court': doc['docsource']}
                    tocwriter.writerow(toc)
                if not self.docs_count:
                    if self.pathbysrc:
                        docpath = self.storage.get_docpath(doc['docsource'], doc['publishdate'])
                    else:    
                        docpath = self.storage.get_docpath_by_position(datadir, current)
                        
                    self.download_doc(docid, docpath)
                unique_docs.add(int(docid))
                current += 1
            if  not self.docs_count and self.csv_output:
                tochandle.flush()
            pagenum += self.maxpages 
        if  not self.docs_count and self.csv_output:
            tochandle.close()
        
        if self.docs_count:
            self.logger.info("%d document(s) found for query: %s %s",len(unique_docs),q,log_stmt)
        return unique_docs   

    def worker(self):
        while True:
            #logger.debug('Waiting for doc to process')
            q = self.queue.get()
            if q == None:
                #logger.warning('Got the sentinel, quitting')
                break

            self.logger.info('Processing %s', q)

            self.save_search_results(q)

            self.logger.info('Done with query %s', q)

    def execute_tasks(self, queries):
        workers = []
        for i in range(0, self.num_workers):
            process =  multiprocessing.Process(target = self.worker)
            process.start()
            workers.append(process)
      
        for q in queries:
            q = self.make_query(q)
            self.queue.put(q)

        for process in workers:
            self.queue.put(None)

        for process in workers:
            process.join()

def get_dateobj(datestr):
    ds = re.findall('\\d+', datestr)
    return datetime.date(int(ds[0]), int(ds[1]), int(ds[2]))

def mk_dir(datadir):
    if not os.path.exists(datadir):
        try:
            os.mkdir(datadir)
        except FileExistsError as e:
            pass

class FileStorage:
    def __init__(self, datadir):
        self.datadir = datadir
        self.logger  = logging.getLogger('filestorage')

    def save_json(self, results, filepath):
        json_doc  = results
        json_file = codecs.open(filepath, mode = 'w', encoding = 'utf-8')
        json_file.write(json_doc)
        json_file.close()

    def exists(self, filepath):
        if os.path.exists(filepath):
            return True
        else:
            return False

    def exists_original(self, origpath):
        return glob.glob('%s.*' % origpath)

    def get_docpath(self, docsource, publishdate):
        datadir = os.path.join(self.datadir, docsource)
        mk_dir(datadir)

        d = get_dateobj(publishdate)
        datadir = os.path.join(datadir, '%d' % d.year)
        mk_dir(datadir)

        docpath = os.path.join(datadir, '%s' % d)
        mk_dir(docpath)

        return docpath

    def get_file_extension(self, mtype):
        t = 'unkwn'
        if not mtype:
            pass 
        elif re.match('text/html', mtype):
            t = 'html'
        elif re.match('application/postscript', mtype):
            t = 'ps'
        elif re.match('application/pdf', mtype):
            t = 'pdf'
        elif re.match('text/plain', mtype):
            t = 'txt'
        elif re.match('image/png', mtype):
            t = 'png'
        return t 

    def save_original(self, orig, origpath):
        try:
            obj = json.loads(orig)
        except Exception as e:
            self.logger.warning('Original is not a correct json %s',  e)
            return False
            
        if 'errmsg' in obj:
            return False

        doc = base64.b64decode(obj['doc'])

        extension = self.get_file_extension(obj['Content-Type'])

        filepath   = origpath + '.%s' % extension
        filehandle = open(filepath, 'wb')
        filehandle.write(doc)
        filehandle.close()
        return True

    def get_docpath_by_docid(self, docid):
        docpath = os.path.join(self.datadir, '%d' % docid)
        mk_dir(docpath)
        return docpath

    def get_json_orig_path(self, docpath, docid):
        jsonpath = os.path.join(docpath, '%d.json' % docid)
        origpath = os.path.join(docpath, '%d_original' % docid)
        return jsonpath, origpath

    def get_json_path(self, q):
        jsonpath = os.path.join(self.datadir, '%s.json' % q)
        return jsonpath

    def get_search_path(self, q):
        datadir = os.path.join(self.datadir, q)
        mk_dir(datadir)
        return datadir

    def get_tocwriter(self, datadir):
        fieldnames = ['position', 'docid', 'date', 'court', 'title']
        tocfile   = os.path.join(datadir, 'toc.csv')
        tochandle = open(tocfile, 'w', encoding = 'utf8')
        tocwriter = csv.DictWriter(tochandle, fieldnames=fieldnames)
        tocwriter.writeheader()
        return tochandle, tocwriter

    def get_docpath_by_position(self, datadir, current):
        docpath = os.path.join(datadir, '%d' % current)
        mk_dir(docpath)
        return docpath

def get_arg_parser():
    parser = argparse.ArgumentParser(description='For downloading from the api.indiankanoon.org endpoint', add_help=True)
    parser.add_argument('-l', '--loglevel', dest='loglevel', action='store',\
                        required = False, default = 'info', \
                        help='log level(error|warning|info|debug)')

    parser.add_argument('-g', '--logfile', dest='logfile', action='store',\
                        required = False, default = None, help='log file')
   
    parser.add_argument('-c', '--doctype', dest='doctype', action='store',\
                        required= False, help='doctype')
    parser.add_argument('-f', '--fromdate', dest='fromdate', action='store',\
                        required= False, help='from date in DD-MM-YYYY format')
    parser.add_argument('-t', '--todate', dest='todate', action='store',\
                        required= False, help='to date in DD-MM-YYYY format')
    parser.add_argument('-S', '--sortby', dest='sortby', action='store',\
                        required= False, help='sort results by (mostrecent|leastrecent)')

    parser.add_argument('-D', '--datadir', dest='datadir', action='store',\
                        required= True,help='directory to store files')
    parser.add_argument('-s', '--sharedtoken', dest='token', action='store',\
                        required= True,help='api.ik shared token')

    parser.add_argument('-q', '--query', dest='q', action='store',\
                        required = False, help='ik query')
    parser.add_argument('-Q', '--qfile', dest='qfile', action='store',\
                        required = False, help='queries in a file')
    parser.add_argument('-d', '--docid', type = int, dest='docid', \
                        action='store', required = False, help='ik docid')

    parser.add_argument('-o', '--original', dest='orig', action='store_true',\
                        required = False,   help='ik original')

    parser.add_argument('-m', '--maxcites', type = int, dest='maxcites', \
                        action='store', default = 0, required = False, \
                        help='doc maxcites')
    parser.add_argument('-M', '--maxcitedby', type = int, dest='maxcitedby', \
                        action='store', default = 0, required = False, \
                        help='doc maxcitedby')
    parser.add_argument('-p', '--maxpages', type = int, dest='maxpages', \
                        action='store', required = False, \
                        help='max search result pages', default=1)
    parser.add_argument('-P', '--pathbysrc', dest='pathbysrc', \
                        action='store_true', required = False, \
                        help='save docs by src')
    parser.add_argument('-a', '--addedtoday', dest='addedtoday', \
                        action='store_true', required = False, default = False,\
                        help='Search only for documents that were added today')
    parser.add_argument('-N', '--workers', type = int, dest='numworkers', \
                        action='store', default = 5, required = False, \
                        help='num workers for parallel downloads')
    parser.add_argument('-C','--citedby', type = int, nargs='+',dest = 'citedby', \
                        action = 'store',required= False, help= 'Fetch citedby for list of docid(s)')
    parser.add_argument('-x','--no-csv',dest='csv_output',action='store_false', \
                        required=False,help = "Do not generate CSV output (default: CSV is generated)")
    parser.add_argument('-n','--count',dest='docs_count',action='store_true',\
                        required=False,help='Displays the number of documents extracted from the results instead of saving search results')
    parser.add_argument('-r','--level',dest='level',action="store_true",\
                        required=False,help="Process next one level of citedby for docid")
    return parser

logformat   = '%(asctime)s: %(name)s: %(levelname)s %(message)s'
dateformat  = '%Y-%m-%d %H:%M:%S'

def initialize_file_logging(loglevel, filepath):
    logging.basicConfig(\
        level    = loglevel,   \
        format   = logformat,  \
        datefmt  = dateformat, \
        stream   = filepath
    )

def initialize_stream_logging(loglevel = logging.INFO):
    logging.basicConfig(\
        level    = loglevel,  \
        format   = logformat, \
        datefmt  = dateformat \
    )

def setup_logging(level, filename = None):
    leveldict = {'critical': logging.CRITICAL, 'error': logging.ERROR, \
                 'warning': logging.WARNING,   'info': logging.INFO, \
                 'debug': logging.DEBUG}
    loglevel = leveldict[level]

    if filename:
        filestream = codecs.open(filename, 'w', encoding='utf8')
        initialize_file_logging(loglevel, filestream)
    else:
        initialize_stream_logging(loglevel)

def extract_docids_from_links(doc_links):
    href = [link['href'] for link in doc_links]
    extracted_docids = [int(re.search(r'/doc/(\d+)/', link).group(1)) for link in href if re.search(r'/doc/(\d+)/', link)]
    return extracted_docids


def process_level(doc_id,unique_docs_toProcess,ikapi):
    unique_docs = set()
    document = json.loads(ikapi.fetch_doc(doc_id))
    if document and document['doc']:
        doc_html = BeautifulSoup(document['doc'],'html.parser')
        doc_links = doc_html.find_all('a',href=lambda x: x and x.startswith('/doc/'))
        extract_docids = extract_docids_from_links(doc_links)
        for id in extract_docids:
            if id not in unique_docs_toProcess:
                log_stmt = "in docid: "+ str(doc_id)
                docs = ikapi.fetch_citedby_docs(id,log_stmt)
                unique_docs |= docs
                unique_docs_toProcess.add(id)
                
    return unique_docs           


if __name__ == '__main__':
    parser = get_arg_parser()
    args   = parser.parse_args()

    setup_logging(args.loglevel, filename = args.logfile)

    logger = logging.getLogger('ikapi')

    filestorage = FileStorage(args.datadir) 
    ikapi       = IKApi(args, filestorage)

    has_more = True


    if args.docid != None and args.q:
        logger.warning('Docfragment for %d q: %s', args.docid, args.q)
        ikapi.save_doc_fragment(args.docid, args.q)
    elif args.docid != None:
        ikapi.download_doc(args.docid, args.datadir)
    elif args.q:
        q = args.q
        if args.addedtoday:
            q += ' added:today'
        logger.warning('Search q: %s', q)
        ikapi.save_search_results(q)
    elif args.doctype:
        ikapi.download_doctype(args.doctype)
    elif args.qfile:
        queries = []
        filehandle = open(args.qfile, 'r', encoding='utf8')
        for line in filehandle.readlines():
            queries.append(line.strip())
        ikapi.execute_tasks(queries)
        filehandle.close() 
    elif args.citedby:
        try:
            for doc_id in args.citedby:
                unique_docs =set()
                unique_docs_toProcess = set()
                unique_docs |= ikapi.fetch_citedby_docs(doc_id)
                unique_docs_toProcess.add(doc_id)
                
                if args.level:
                    unique_docs |= process_level(doc_id,unique_docs_toProcess,ikapi)
                
                if not args.level:
                    logger.info("Total documents cited by docid %d: %d",doc_id,len(unique_docs))
                else:
                    logger.info("Total documents cited by docid %d with level: %d",doc_id,len(unique_docs))
        except Exception as e:
            logger.error("Exception arised while fetching citedby for docid : %d - %s" %(doc_id,str(e)))