(ns git-hooks
  (:require
   [babashka.fs :as fs]
   [babashka.pods :as pods]
   [babashka.tasks :as tasks]
   [clojure.java.shell :refer [sh]]
   [clojure.string :as str]))

(pods/load-pod "clj-kondo")
(require '[pod.borkdude.clj-kondo :as clj-kondo])

(defn ^:private changed-files
  []
  (->> (sh "git" "diff" "--name-only" "--cached" "--diff-filter=ACM")
       :out
       str/split-lines
       (filter seq)
       seq))

(def ^:private clj-extensions
  #{"clj" "cljx" "cljc" "cljs" "edn"})

(defn ^:private clj?
  [s]
  (when s
    (let [extension (last (str/split s #"\."))]
      (clj-extensions extension))))

(defn ^:private hook-text
  [hook]
  (format "#!/bin/sh
# Installed by babashka task on %s

bb hooks %s" (java.util.Date.) hook))

(defn ^:private spit-hook
  [hook]
  (println "Installing hook: " hook)
  (let [file (str ".git/hooks/" hook)]
    (spit file (hook-text hook))
    (fs/set-posix-file-permissions file "rwx------")
    (assert (fs/executable? file))))

(defmulti hooks
  "Multimethod for Git hook commands"
  (fn [& args] (first args)))

(defmethod hooks "install" [& _]
  (tasks/clojure "-Ttools install-latest :lib io.github.weavejester/cljfmt :as cljfmt")
  (spit-hook "pre-commit"))

(defmethod hooks "pre-commit" [& _]
  (println "Running pre-commit hook")
  (when-let [files (changed-files)]
    ;; clojure
    (when-let [clj-files (seq (filter clj? files))]
      (clj-kondo/print!
       (clj-kondo/run! {:lint clj-files}))
      (tasks/clojure (str "-Tcljfmt check :paths '" (prn-str clj-files) "'")))))

(defmethod hooks :default [& args]
  (println "Unknown command:" (first args)))
