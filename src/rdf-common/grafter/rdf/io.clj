(ns grafter.rdf.io
  (:require [clojure.java.io :as io]
            [grafter.rdf.protocols :as pr]
            [clojure.tools.logging :as log]
            [pantomime.media :as mime]
            )
  (:import (grafter.rdf.protocols IStatement Quad Triple)
           (java.io File)
           (java.net MalformedURLException URL)
           (java.util GregorianCalendar)
           (javax.xml.datatype DatatypeFactory)
           (org.openrdf.model BNode Literal Resource Statement URI
                              Value)
           (org.openrdf.model.impl BNodeImpl BooleanLiteralImpl
                                   CalendarLiteralImpl
                                   ContextStatementImpl
                                   IntegerLiteralImpl LiteralImpl
                                   NumericLiteralImpl StatementImpl
                                   URIImpl)
           ;(org.openrdf.query.impl DatasetImpl)
           (org.openrdf.repository Repository RepositoryConnection)
           ;(org.openrdf.repository.http HTTPRepository)
           ;(org.openrdf.repository.sail SailRepository)
           ;(org.openrdf.repository.sparql SPARQLRepository)
           (org.openrdf.rio RDFFormat RDFHandler RDFWriter Rio RDFParserFactory RDFParser)
           (org.openrdf.rio.n3 N3ParserFactory)
           (org.openrdf.rio.nquads NQuadsParserFactory)
           (org.openrdf.rio.ntriples NTriplesParserFactory)
           (org.openrdf.rio.rdfjson RDFJSONParserFactory)
           (org.openrdf.rio.rdfxml RDFXMLParserFactory)
           (org.openrdf.rio.trig TriGParserFactory)
           (org.openrdf.rio.trix TriXParserFactory)
           (org.openrdf.rio.turtle TurtleParserFactory)
           ;(org.openrdf.sail.memory MemoryStore)
           ;(org.openrdf.sail.nativerdf NativeStore)
           ;(info.aduna.iteration CloseableIteration)
           ))


(extend-type Statement
  ;; Extend our IStatement protocol to Sesame's Statements for convenience.
  pr/IStatement
  (subject [this] (.getSubject this))
  (predicate [this] (.getPredicate this))
  (object [this] (.getObject this))
  (context [this] (.getContext this)))

(defprotocol ISesameRDFConverter
  (->sesame-rdf-type [this] "Convert a native type into a Sesame RDF Type")
  (sesame-rdf-type->type [this] "Convert a Sesame RDF Type into a Native Type"))

(defn s
  "Cast a string to an RDF literal.  The second optional argument can
  either be a keyword corresponding to an RDF language tag
  e.g. :en, :en-gb, or :fr or a string or URI in which case it is
  assumed to be a URI identifying the RDF type of the literal."
  ([str]
     {:pre [(string? str)]}
     (reify Object
       (toString [_] str)
       ISesameRDFConverter
       (->sesame-rdf-type [this]
         (LiteralImpl. str))))
  ([^String str lang-or-uri]
     {:pre [(string? str) (or (string? lang-or-uri) (keyword? lang-or-uri) (nil? lang-or-uri) (instance? URI lang-or-uri))]}
     (reify Object
       (toString [_] str)
       ISesameRDFConverter
       (->sesame-rdf-type [this]
         (if (instance? URI lang-or-uri)
           (let [^URI uri lang-or-uri] (LiteralImpl. str uri))
           (let [^String t (and lang-or-uri (name lang-or-uri))]
             (LiteralImpl. str t)))))))

(defmulti literal-datatype->type
  "A multimethod to convert an RDF literal into a corresponding
  Clojure type.  This method can be extended to provide custom
  conversions."
  (fn [^Literal literal]
    (when-let [datatype (-> literal .getDatatype)]
      (str datatype))))

(defmethod literal-datatype->type nil [^Literal literal]
  (s (.stringValue literal) (.getLanguage literal)))

(defmethod literal-datatype->type "http://www.w3.org/2001/XMLSchema#byte" [^Literal literal]
  (.byteValue literal))

(defmethod literal-datatype->type "http://www.w3.org/2001/XMLSchema#short" [^Literal literal]
  (.shortValue literal))

(defmethod literal-datatype->type "http://www.w3.org/2001/XMLSchema#decimal" [^Literal literal]
  (.decimalValue literal))

(defmethod literal-datatype->type "http://www.w3.org/2001/XMLSchema#double" [^Literal literal]
  (.doubleValue literal))

(defmethod literal-datatype->type "http://www.w3.org/2001/XMLSchema#float" [^Literal literal]
  (.floatValue literal))

(defmethod literal-datatype->type "http://www.w3.org/2001/XMLSchema#integer" [^Literal literal]
  (.integerValue literal))

(defmethod literal-datatype->type "http://www.w3.org/2001/XMLSchema#int" [^Literal literal]
  (.intValue literal))

(defmethod literal-datatype->type "http://www.w3.org/TR/xmlschema11-2/#string" [^Literal literal]
  (s (.stringValue literal) (.getLanguage literal)))

(defmethod literal-datatype->type "http://www.w3.org/2001/XMLSchema#dateTime" [^Literal literal]
  (-> literal .calendarValue .toGregorianCalendar .getTime))

(defmethod literal-datatype->type :default [^Literal literal]
  ;; If we don't have a type conversion for it, let the sesame type
  ;; through, as it's not really up to grafter to fail the processing,
  ;; as they might just want to pass data through rather than
  ;; understand it.
  literal)

(extend-protocol ISesameRDFConverter
  ;; Numeric Types

  java.lang.Byte
  (->sesame-rdf-type [this]
    (NumericLiteralImpl. this (URIImpl. "http://www.w3.org/2001/XMLSchema#byte")))

  (sesame-rdf-type->type [this]
    this)

  java.lang.Short
  (->sesame-rdf-type [this]
    (NumericLiteralImpl. this (URIImpl. "http://www.w3.org/2001/XMLSchema#short")))

  (sesame-rdf-type->type [this]
    this)

  java.math.BigDecimal
  (->sesame-rdf-type [this]
    (NumericLiteralImpl. this (URIImpl. "http://www.w3.org/2001/XMLSchema#decimal")))

  (sesame-rdf-type->type [this]
    this)

  java.lang.Double
  (->sesame-rdf-type [this]
    (NumericLiteralImpl. this))

  (sesame-rdf-type->type [this]
    this)

  java.lang.Float
  (->sesame-rdf-type [this]
    (NumericLiteralImpl. this))

  (sesame-rdf-type->type [this]
    this)

  java.lang.Integer
  (->sesame-rdf-type [this]
    (NumericLiteralImpl. this))

  (sesame-rdf-type->type [this]
    this)

  java.math.BigInteger
  (->sesame-rdf-type [this]
    (NumericLiteralImpl. this (URIImpl. "http://www.w3.org/2001/XMLSchema#integer")))

  (sesame-rdf-type->type [this]
    this)

  java.lang.Long
  (->sesame-rdf-type [this]
    ;; hacky and probably a little slow but works for now
    (IntegerLiteralImpl. (BigInteger. (str this))))

  (sesame-rdf-type->type [this]
    this)

  clojure.lang.BigInt
  (->sesame-rdf-type [this]
    ;; hacky and probably a little slow but works for now
    (IntegerLiteralImpl. (BigInteger. (str this))))

  (sesame-rdf-type->type [this]
    this))

(extend-protocol ISesameRDFConverter

  java.lang.Boolean
  (->sesame-rdf-type [this]
    (BooleanLiteralImpl. this))

  (sesame-rdf-type->type [this]
    this)

  BooleanLiteralImpl
  (->sesame-rdf-type [this]
    this)

  (sesame-rdf-type->type [this]
    (.booleanValue this))

  java.lang.String
  ;; Assume URI's are the norm not strings
  (->sesame-rdf-type [this]
    (URIImpl. this))

  (sesame-rdf-type->type [this]
    this)

  LiteralImpl
  (->sesame-rdf-type [this]
    this)

  (sesame-rdf-type->type [this]
    (literal-datatype->type this))

  Statement
  (->sesame-rdf-type [this]
    this)

  (sesame-rdf-type->type [this]
    this)

  Triple
  (->sesame-rdf-type [this]
    (StatementImpl. (->sesame-rdf-type (pr/subject this))
                    (->sesame-rdf-type (pr/predicate this))
                    (->sesame-rdf-type (pr/object this))))

  Quad
  (->sesame-rdf-type [this]
    (ContextStatementImpl. (->sesame-rdf-type (pr/subject this))
                           (->sesame-rdf-type (pr/predicate this))
                           (->sesame-rdf-type (pr/object this))
                           (when-let [context (pr/context this)]
                             (->sesame-rdf-type context))))

  Value
  (->sesame-rdf-type [this]
    this)

  Resource
  (->sesame-rdf-type [this]
    this)

  Literal
  (->sesame-rdf-type [this]
    this)

  (sesame-rdf-type->type [this]
    (literal-datatype->type this))

  URI
  (->sesame-rdf-type [this]
    this)

  (sesame-rdf-type->type [this]
    (str this))

  java.net.URI
  (->sesame-rdf-type [this]
    (URIImpl. (.toString this)))

  java.net.URL
  (->sesame-rdf-type [this]
    (URIImpl. (.toString this)))

  BNode
  (->sesame-rdf-type [this]
    this)

  (sesame-rdf-type->type [this]
    (-> this .getID keyword))

  BNodeImpl
  (->sesame-rdf-type [this]
    this)

  (sesame-rdf-type->type [this]
    (-> this .getID keyword))

  java.util.Date
  (->sesame-rdf-type [this]
    (let [cal (doto (GregorianCalendar.)
                (.setTime this))]
      (-> (DatatypeFactory/newInstance)
          (.newXMLGregorianCalendar cal)
          CalendarLiteralImpl.)))

  clojure.lang.Keyword
  (->sesame-rdf-type [this]
    (BNodeImpl. (name this))))

(defn IStatement->sesame-statement
  "Convert a grafter IStatement into a Sesame statement."
  [^IStatement is]
  (if (pr/context is)
    (do
      (ContextStatementImpl. (->sesame-rdf-type (pr/subject is))
                             (URIImpl. (pr/predicate is))
                             (->sesame-rdf-type (pr/object is))
                             (URIImpl. (pr/context is))))
    (StatementImpl. (->sesame-rdf-type (pr/subject is))
                    (URIImpl. (pr/predicate is))
                    (->sesame-rdf-type (pr/object is)))))

(defn sesame-statement->IStatement
  "Convert a sesame Statement into a grafter Quad."
  [^Statement st]
  ;; TODO fix this to work properly with object & context.
  ;; context should return either nil or a URI
  ;; object should be converted to a clojure type.
  (Quad. (str (.getSubject st))
         (str (.getPredicate st))
         (sesame-rdf-type->type (.getObject st))
         (when-let [graph (.getContext st)]
           (sesame-rdf-type->type graph))))

(defn rdf-serializer
  "Coerces destination into an java.io.Writer using
  clojure.java.io/writer and returns an RDFSerializer.

  Accepts also the following optional options:

  :append          If set to true it will append new values to the end of
                   the file destination (default: false).

  :format          If a String or a File are provided the format parameter
                   can be optional (in which case it will be infered from
                   the file extension).  This should be a sesame RDFFormat
                   object.

  :encoding        The character encoding to be used (default: UTF-8)"

  ([destination & {:keys [append format encoding] :or {append false
                                                       encoding "UTF-8"}}]
     (let [^RDFFormat format (or format
                      (condp = (class destination)
                        String (Rio/getWriterFormatForFileName destination)
                        File   (Rio/getWriterFormatForFileName (str destination))
                        (throw (ex-info "Could not infer file format, please supply a :format parameter" {:error :could-not-infer-file-format :object destination}))))]
       (Rio/createWriter format
                         (io/writer destination
                                    :append append
                                    :encoding encoding)))))

(extend-protocol pr/ITripleWriteable
  RDFWriter
  (pr/add-statement [this statement]
    (.handleStatement this (->sesame-rdf-type statement)))

  (pr/add
    ([this triples]
       (if (seq triples)
         (do
           (.startRDF this)
           (doseq [t triples]
             (pr/add-statement this t))
           (.endRDF this))
         (throw (IllegalArgumentException. "This serializer does not support writing a single statement.  It should be passed a sequence of statements."))))

    ([this _graph triples]
       ;; TODO if format allows graphs we should support
       ;; them... otherwise.. ignore the graph param
       (pr/add this triples))))

(defn ^:no-doc format->parser
  "Convert a format into a sesame parser for that format."
  ^RDFParser
  [format]
  (let [table {RDFFormat/NTRIPLES NTriplesParserFactory
               RDFFormat/TRIX TriXParserFactory
               RDFFormat/TRIG TriGParserFactory
               RDFFormat/RDFXML RDFXMLParserFactory
               RDFFormat/NQUADS NQuadsParserFactory
               RDFFormat/TURTLE TurtleParserFactory
               RDFFormat/JSONLD RDFJSONParserFactory
               RDFFormat/N3 N3ParserFactory
               }
        ^Class parser-class (table format)]
    (if-not parser-class
      (throw (ex-info (str "Unsupported format: " (pr-str format)) {:type :unsupported-format})))
    (let [^RDFParserFactory factory (.newInstance parser-class)]
      (.getParser factory))))

(defn filename->rdf-format
  "Given a filename we attempt to return an appropriate RDFFormat
  object based on the files extension."
  [fname]
  (Rio/getParserFormatForFileName fname))

(defn mimetype->rdf-format
  "Given a mimetype string we attempt to return an appropriate
  RDFFormat object based on the files extension."
  [mime-type]
  (let [base-type (str (mime/base-type mime-type))]
    (condp = base-type
      "application/n-triples" RDFFormat/NTRIPLES ;; Sesame doesn't yet support application/n-triples
      (Rio/getParserFormatForMIMEType mime-type))))

;; http://clj-me.cgrand.net/2010/04/02/pipe-dreams-are-not-necessarily-made-of-promises/
(defn- pipe
  "Returns a pair: a seq (the read end) and a function (the write end).
  The function can takes either no arguments to close the pipe
  or one argument which is appended to the seq. Read is blocking."
  [^Integer size]
  (let [q (java.util.concurrent.LinkedBlockingQueue. size)
        EOQ (Object.)
        NIL (Object.)
        pull (fn pull [] (lazy-seq (let [x (.take q)]
                                    (when-not (= EOQ x)
                                      (cons (when-not (= NIL x) x) (pull))))))]
    [(pull) (fn put! ([] (.put q EOQ)) ([x] (.put q (or x NIL))))]))

(extend-protocol pr/ITripleReadable

  String
  (pr/to-statements [this options]
    (try
      (pr/to-statements (URL. this) options)
      (catch MalformedURLException ex
        (pr/to-statements (File. this) options))))

  URL
  (pr/to-statements [this options]
    (pr/to-statements (io/reader this) options))

  URI
  (pr/to-statements [this options]
    (pr/to-statements (str this) options))

  File
  (pr/to-statements [this opts]
    (let [implied-format (Rio/getParserFormatForFileName (str this))
          options (if implied-format
                    (merge {:format implied-format} opts)
                    opts)]
      (pr/to-statements (io/reader this) options)))

  java.io.Reader
  ;; WARNING: This implementation is necessarily a little convoluted
  ;; as we hack around Sesame to generate a lazy sequence of results.
  ;; Sesame's parse methods always assume you want to consume the
  ;; whole file of triples, so we spawn a thread to consume through
  ;; the file and use a blocking queue of 1 element to pass elements
  ;; back into a lazy sequence on the calling thread.  The queue has a
  ;; bounded size of 1 forcing it be in lockstep with the consumer.
  ;;
  ;; NOTE also none of these functions really allow for proper
  ;; resource clean-up unless the whole sequence is consumed.
  ;;
  ;; So, the good news is that this means you should be able to read
  ;; and stream huge files.  The bad news is that might leak a file
  ;; handle, unless you consume the whole sequence.
  ;;
  ;; TODO: consider how to support proper resource cleanup.
  (pr/to-statements [reader {:keys [format buffer-size] :or {buffer-size 32} :as options}]
    (if-not format
      (throw (ex-info (str "The RDF format was neither specified nor inferable from this object.") {:type :no-format-supplied}))
      (let [[statements put!] (pipe buffer-size)]
        (future
          (let [parser (doto (format->parser format)
                         (.setRDFHandler (reify RDFHandler
                                           (startRDF [this])
                                           (endRDF [this]
                                             (put!)
                                             (.close reader))
                                           (handleStatement [this statement]
                                             (put! statement))
                                           (handleComment [this comment])
                                           (handleNamespace [this prefix-str uri-str]))))]
            (try
              (.parse parser reader "http://example.org/base-uri")
              (catch Exception ex
                (put! ex)))))
        (let [read-rdf (fn read-rdf [msg]
                         (if (instance? Exception msg)
                           ;; if the other thread puts an Exception on
                           ;; the pipe, raise it here.
                           (throw (ex-info "Reading triples aborted."
                                           {:type :reading-aborted} msg))
                           (sesame-statement->IStatement msg)))]
          (map read-rdf statements))))))
