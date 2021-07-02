(defproject add-image-dimensions-to-html "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "EPL-2.0 OR GPL-2.0-or-later WITH Classpath-exception-2.0"
            :url "https://www.eclipse.org/legal/epl-2.0/"}
  :dependencies [[org.clojure/clojure "1.10.1"]
                 [tubular "1.4.0"]
                 [babashka/babashka.pods "0.0.1"]
                 [babashka/babashka.curl "0.0.3"]
                 [babashka/fs "0.0.5"]
                 [enlive "1.1.6"]]
  :repl-options {:init-ns add-image-dimensions-to-html.core})
