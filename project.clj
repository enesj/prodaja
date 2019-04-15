(defproject prodaja "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :url "http://example.com/FIXME"
  :license {:name "Eclipse Public License"
            :url "http://www.eclipse.org/legal/epl-v10.html"}
  :dependencies [[clj-time "0.14.4"]
                 [com.rpl/specter "1.1.1"]
                 [com.microsoft.sqlserver/mssql-jdbc "7.0.0.jre8"]
                 [dk.ative/docjure "1.7.0"]
                 [honeysql "0.9.3"]
                 [org.clojure/clojure "1.10.0-RC1"]
                 [org.clojure/java.jdbc "0.7.8"]
                 ;[org.clojure/spec.alpha "0.2.176"]
                 [org.clojure/test.check "0.10.0-alpha3"]]
 :jvm-opts ["-Dclojure.server.repl={:port 5555 :accept clojure.core.server/repl}"]) ; notice that the map is not quoted
