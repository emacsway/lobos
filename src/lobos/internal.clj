; Copyright (c) Nicolas Buduroi. All rights reserved.
; The use and distribution terms for this software are covered by the
; Eclipse Public License 1.0 which can be found in the file
; epl-v10.html at the root of this distribution. By using this software
; in any fashion, you are agreeing to be bound by the terms of this
; license.
; You must not remove this notice, or any other, from this software.

(ns lobos.internal
  (:refer-clojure :exclude [defonce])
  (:require (lobos [compiler :as compiler]
                   [connectivity :as conn]
                   [schema :as schema]))
  (:use (clojure.contrib [def :only [name-with-attributes]])
        lobos.utils))

(defonce debug-level
  (atom nil)
  "This atom keeps the currently set debug level, see
  `set-debug-level`. *For internal use*.")

(defn- execute*
  "Execute the given SQL string or sequence of strings. Prints them if
  the `debug-level` is set to `:sql`."
  [sql]
  (doseq [sql-string (if (seq? sql) sql [sql])]
    (when (= :sql @debug-level) (println sql-string))
    (with-open [stmt (.createStatement (conn/connection))]
      (.execute stmt sql-string))))

(defn execute
  "Executes the given statement(s) using the specified connection
  information, the bound one or the default connection. It will executes
  an extra *mode* statement if defined by the backend compiler. *For
  internal purpose*."
  [statements & [connection-info]]
  (let [statements (if (seq? statements)
                     statements
                     [statements])
        db-spec (conn/get-db-spec connection-info)
        mode (compiler/compile (compiler/mode db-spec))]
    (conn/with-connection connection-info
      (require (symbol (str "lobos.backends."
                            (:subprotocol connection-info))))
      (when mode (execute* mode))
      (doseq [statement statements]
        (let [sql (if (string? statement)
                           statement
                           (compiler/compile statement))]
          (when sql (execute* sql)))))) nil)

(defn optional-cnx-or-schema [args]
  (let [[cnx-or-schema args]
        (optional #(or (schema/schema? %)
                       (and (-> % schema/definition? not)
                            (conn/connection? %))) args)
        args* args
        schema (when (schema/schema? cnx-or-schema) cnx-or-schema)
        cnx (or (conn/find-connection)
                (-> schema :options :db-spec)
                (when-not schema cnx-or-schema)
                :default-connection)
        db-spec (merge (conn/get-db-spec cnx)
                       (when schema
                         {:schema (-> schema :sname name)}))]
    [db-spec schema args]))