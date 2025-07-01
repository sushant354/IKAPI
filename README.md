# IKAPI
Tools to access Indian Kanoon API in Python/Java

Fetch our package and go to the python/java directory as per your requirement
```
git clone https://github.com/sushant354/IKAPI.git
```
PYTHON
======

Go to Python folder

```
cd python
```

Install the dependencies

```
pip install beautifulsoup4
```


```
usage: ikapi.py [-h] [-l LOGLEVEL] [-g LOGFILE] [-c DOCTYPE] [-f FROMDATE]  [-t TODATE] [-S SORTBY] -D DATADIR -s TOKEN [-q Q] [-Q QFILE] [-d DOCID] [-o] [-m MAXCITES] [-M MAXCITEDBY] [-p MAXPAGES] [-P] [-a] [-N NUMWORKERS] [-C CITEDBY]

For downloading from the api.indiankanoon.org endpoint

options:
  -h, --help            show this help message and exit
  -l LOGLEVEL, --loglevel LOGLEVEL
                        log level(error|warning|info|debug)
  -g LOGFILE, --logfile LOGFILE
                        log file
  -c DOCTYPE, --doctype DOCTYPE
                        doctype
  -f FROMDATE, --fromdate FROMDATE
                        from date in DD-MM-YYYY format
  -t TODATE, --todate TODATE
                        to date in DD-MM-YYYY format
  -S SORTBY, --sortby SORTBY
                        sort results by (mostrecent|leastrecent)
  -D DATADIR, --datadir DATADIR
                        directory to store files
  -s TOKEN, --sharedtoken TOKEN
                        api.ik shared token
  -q Q, --query Q       ik query
  -Q QFILE, --qfile QFILE
                        queries in a file
  -d DOCID, --docid DOCID
                        ik docid
  -o, --original        ik original
  -m MAXCITES, --maxcites MAXCITES
                        doc maxcites
  -M MAXCITEDBY, --maxcitedby MAXCITEDBY
                        doc maxcitedby
  -p MAXPAGES, --maxpages MAXPAGES
                        max search result pages
  -P, --pathbysrc       save docs by src
  -a, --addedtoday      Search only for documents that were added today
  -N NUMWORKERS, --workers NUMWORKERS
                        num workers for parallel downloads
  -C CITEDBY, --citedby CITEDBY
                        citedby docs for docid
  -x, --no-csv          Do not generate CSV output (default: CSV is generated)
```
JAVA
=====
Install mvn, OpenJDK. On Ubuntu:
```
apt install mvn
```
Then compile the package:
```
mvn clean package
java -jar target/ikapi-1.0.0.jar <options>
```

On linux systems
```
./run.sh <options>

usage: IKApi [-h] [-l LOGLEVEL] [-g LOGFILE] [-c DOCTYPE] [-f FROMDATE]
             [-t TODATE] [-S SORTBY] -D DATADIR -s TOKEN [-q Q] [-Q QFILE]
             [-d DOCID] [-o] [-m MAXCITES] [-M MAXCITEDBY] [-p MAXPAGES]
             [-P] [-a] [-N NUMWORKERS]

For downloading from the api.indiankanoon.arg endpoint

named arguments:
  -h, --help             show this help message and exit
  -l LOGLEVEL, --loglevel LOGLEVEL
                         log level(severe [for  error/critical]  || warning
                         || info || finest [for debug]) (default: info)
  -g LOGFILE, --logfile LOGFILE
                         log file
  -c DOCTYPE, --doctype DOCTYPE
                         doctype
  -f FROMDATE, --fromdate FROMDATE
                         from date in DD-MM-YYYY format
  -t TODATE, --todate TODATE
                         to date in DD-MM-YYYY format
  -S SORTBY, --sortby SORTBY
                         sort results by (mostrecent|leastrecent)
  -D DATADIR, --datadir DATADIR
                         directory to store files
  -s TOKEN, --sharedtoken TOKEN
                         api.ik shared token
  -q Q, --query Q        ik query
  -Q QFILE, --qfile QFILE
                         queries in a file
  -d DOCID, --docid DOCID
                         ik docid
  -o, --original         ik original (default: false)
  -m MAXCITES, --maxcites MAXCITES
                         doc maxcites (default: 0)
  -M MAXCITEDBY, --maxcitedby MAXCITEDBY
                         doc maxcitedby (default: 0)
  -p MAXPAGES, --maxpages MAXPAGES
                         max search result pages (default: 1)
  -P, --pathbysrc        save docs by src (default: false)
  -a, --addedtoday       Search only the  documents  that  were added today
                         (default: false)
  -N NUMWORKERS, --workers NUMWORKERS
                         num workers for parallel downloads (default: 5)
```

To use the iKapi library in your Java project with Maven, add the following dependency to your pom.xml file:

```
<dependency>
    <groupId>org.indiankanoon</groupId>
    <artifactId>ikapi</artifactId>
    <version>1.0.0</version>
</dependency>
```

Once the dependency is added, you can start using the library in your code.

To make a request, simply execute:

```
IKApiMain.main([options]);
```
