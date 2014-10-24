(ns polismath.intercom
  (:require [clj-http.client :as client]
            [plumbing.core :as pc]
            [cheshire.core :as ch]
            [korma.core :as ko]
            [korma.db :as kdb]
            [environ.core :as env]
            [alex-and-georges.debug-repl :as dbr]
            [clojure.stacktrace :refer :all]
            [clojure.tools.logging :as log]
            [clojure.tools.trace :as tr]
            [polismath.utils :refer :all]
            [polismath.pretty-printers :as pp]
            [polismath.db :as db]))



(def intercom-http-params
  {:accept :json
   :basic-auth ["nb5hla8s" (env/env :intercom-api-key)]
   :content-type :json})


(defn parse-json-resp
  [resp]
  (->> resp
       :body
       ch/parse-string
       (into {})))


(defn get-icusers
  "Get the list of users from intercom (don't want to create intercom users for users that haven't
  actually signed up"
  [& [page]]
  (let [return-data
          (->
            (or page "https://api.intercom.io/users")
            (client/get intercom-http-params)
            :body
            (ch/parse-string)
            (->>
              (into {})))
        next-page (get-in return-data ["pages" "next"])
        users (get return-data "users")]
    (sort-by 
      (fn [u] (int (get u "created_at")))
      (if next-page
        (into users (get-icusers next-page))
        users))))


(defn get-icuser-by-email
  "Get the list of users from intercom (don't want to create intercom users for users that haven't
  actually signed up"
  [email]
  (->
    (str "https://api.intercom.io/users?email=" email)
    (client/get intercom-http-params)
    (parse-json-resp)))


(defn update-icuser!
  "Update an icuser given params for that user"
  [params]
  (->>
    params
    (ch/generate-string)
    (assoc intercom-http-params :body)
    (client/post "https://api.intercom.io/users")))


; XXX - This won't actually work yet, since they don't support updating parameters through the batch post.
; It's now written for when they do though.
(defn update-icusers!
  "WARNING - API not yet implemented! In theory, once it is though, this will update multiple icusers."
  [users]
  (->>
    users
    (assoc {} :users)
    (ch/generate-string)
    (assoc intercom-http-params :body)
    (client/post "https://api.intercom.io/users/bulk")))


(defn icuser-from-dbuser
  "Translates a dbuser into an icuser suitable for uploading to the intercom db."
  [dbuser]
  (into
    {}
    (map
      (fn [[ic-key db-key]]
        [ic-key (db-key dbuser)])
      [[:name              :hname]
       [:user_id           :uid]
       [:email             :email]
       [:custom_attributes
          (pc/fn->
            (hash-map-subset
              [:avg_n_ptpts :n_owned_convs :n_ptptd_convs :n_owned_convs_ptptd :avg_n_visitors]))]
       [:remote_created_at
          #(/ (:created %) 1000)]])))


(defn update-icuser-from-dbuser!
  "Update a single icuser record from a single dbuser record."
  [dbuser]
  (update-icuser! (icuser-from-dbuser dbuser)))


; XXX - This won't actually work yet, since they don't support updating parameters through the batch post.
; It's now written for when they do though.
(defn update-icusers-from-dbusers!
  "WARNING - API not yet implemented! In theory, once it is though, this will update all users based on dbusers."
  [dbusers]
  (->> dbusers
       (map icuser-from-dbuser)
       (update-icusers!)))


; Main functions:
; ===============


(defn backup-intercom-users
  "Backup intercom users to json file specified by filename arg."
  [filename]
  (let [icusers (get-icusers)]
    (spit filename (ch/generate-string icusers))))


(defn future-failed? [fu]
  (try @fu false (catch Exception e true)))


(defn update-intercom-db
  "Update intercom records by grabbing existing records, and from those the corresponding PG DB records
  so tha twe have enough information to flesh out missing information."
  []
  (let [; Get intercom users and break down by those that have id and those that don't, but have email
        _                (println "Fetching data from intercom")
        icusers          (get-icusers)
        icusers-by-id    (filter #(get % "user_id") icusers)
        icusers-by-email (filter #(not (get % "user_id")) icusers)
        ; Get users by id, then by email for those without id, then put them all together in one collection
        _                (println "Fetching pg db records")
        dbusers-by-id    (db/get-users-by-uid
                           (map #(Integer/parseInt (get % "user_id")) icusers-by-id))
        dbusers-by-email (db/get-users-by-email
                           (map #(get % "email") icusers-by-email)) 
        all-users        (into dbusers-by-id dbusers-by-email)
        ; User updates have to be batched, since there is a limit of 240 requests per one minute. Their API
        ; suggests limiting batch size to </= 50.
        batch-size       50
        batched-users    (partition-all 50 all-users)]
    ; First some nice summary stats information
    (println "Total number of intercom users:" (count icusers))
    (println "Number of users with valid ids:" (count icusers-by-id))
    (println "Number w/o:                    " (count icusers-by-email))
    ; Now getting to work
    (println "Now updating all user records in intercom")
    (let [jobs (mapv
                 (fn [u]
                   [u (future
                        (log/info "Running update for user:" (hash-map-subset u [:uid :email :hname :created]))
                        (update-icuser-from-dbuser! u))])
                   all-users)
          failed-jobs (filterv (comp future-failed? second) jobs)]
      (println "Number of failed jobs:" (count failed-jobs))
      (doseq [[u _] failed-jobs]
        (println u)))
    ; Stab at doing batched runs
    ;(let [jobs (mapv
                 ;(fn [us]
                   ;[us
                    ;(future
                      ;(log/info "Running update for" (count us) "users:"
                               ;(map (pc/fn-> (hash-map-subset [:uid :email :hname :created])) us))
                      ;(update-icuser-from-dbuser! us))])
                   ;batched-users)
          ;failed-jobs (filterv (comp future-failed? second) jobs)]
      ;(println "Number of failed jobs:" (count failed-jobs))
      ;(println "Failed for users:" (apply concat (map first failed-jobs))))
    ; Call it a night
    (println "Done!")
    (shutdown-agents)))

