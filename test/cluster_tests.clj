(ns cluster-tests
  (:use polismath.utils)
  (:require [clojure.test :refer :all]
            [polismath.named-matrix :refer :all]
            [polismath.clusters :refer :all]))

(defn size-correct [clusters n]
  ; XXX - really need to rewrite as macro
  (is (= n (count clusters))))


(def real-nmat (named-matrix
  ["p1" "p2" "p3" "p4" "p5" "p6"]
  ["c1" "c2" "c3" "c4"]
  [[ 0  1  0  1]
   [ 0  1  0  1]
   [-1  0 -1  1]
   [ 1  0  1  0]
   [-1 -1  0  1]
   [-1  1  0  1]]))


; Initialization test
(deftest init-test
  (let [n-clusts 2
        ic       (init-clusters real-nmat n-clusts)]
    (testing "gives right number of clusters"
      (size-correct ic 2))
    (testing "doesn't give duplicate clusters"
      (is (not (= (:center (first ic)) (:center (second ic))))))))

(deftest basic-test
  (let [n-clusts 2
        clusts   (kmeans real-nmat n-clusts)]
    (testing "gives right number of clusters"
      (size-correct clusts 2))
    (testing "doesn't give duplicate clusters"
      (is (not (= (:center (first clusts)) (:center (second clusts))))))
    (testing "coordinate transforms should be handled gracefully"
      (let [real-nmat (named-matrix (rownames real-nmat) (colnames real-nmat)
                            [[0.5 0.3]
                             [4.3 0.5]
                             [2.3 8.1]
                             [1.2 4.2]
                             [1.2 2.3]
                             [4.3 1.2]])]
        ; basically just testing here that things don't break
        (is (= 2 (count (kmeans real-nmat 2 :last-clusters clusts))))))))

(deftest growing-clusters
  (let [data (named-matrix
               ["p1" "p2" "p3" "p4" "p5"]
               ["c1" "c2" "c3"]
               [[ 1  1  1]
                [ 1  1  0]
                [-1 -1 -1]
                [-1 -1  0]
                [-1  1  0]])
        last-clusts [{:id 1 :members ["p1" "p2"]} {:id 2 :members ["p3" "p4"]}]
        clusts (kmeans data 3 :last-clusters last-clusts)]
    (testing "should give the right number of clusters"
      (size-correct clusts 3))))


(deftest less-than-k-test
  (testing "k-means on n < k items gives n clusters"
    (let [data (named-matrix
                 ["p1" "p2"]
                 ["c1" "c2" "c3"]
                 [[ 0  1  0 ]
                  [-1  1  0 ]])]
      (size-correct (kmeans data 3) 2))))


(let [data (named-matrix
             ["p1" "p2" "p3"]
             ["c1" "c2" "c3"]
             [[ 0  1  0 ]
              [ 0  1  0 ]
              [-1  1  0 ]])]
  (deftest identical-mem-pos-test
    (testing "k-means gives n-1 clusters when precisely 2 items have identical positions"
        (size-correct (kmeans data 3) 2)))

  (deftest shrinking-test
    (testing "k-means gives n-1 clusters even when n last-clusters have been specified
             if two members have identical positions"
      (let [last-clusts [{:id 1 :members ["p1"]}
                         {:id 2 :members ["p2"]}
                         {:id 3 :members ["p3"]}]]
        (size-correct (kmeans data 3 :last-clusters last-clusts) 2)))))

(deftest edge-cases (less-than-k-test) (identical-mem-pos-test) (shrinking-test))


(deftest most-distal-test
  (let [data (named-matrix
               ["p1" "p2" "p3" "p4" "p5"]
               ["c1" "c2" "c3"]
               [[ 1  1  1]
                [ 1  1  0]
                [-1 -1 -1]
                [-1 -1  0]
                [-1  1  0]])
        clusts [{:id 1 :members ["p1" "p2"]} {:id 2 :members ["p3" "p4" "p5"]}]
        clusts (recenter-clusters data clusts)]
    (testing "correct clst-id"
      (is (= (:clst-id (most-distal data clusts)) 2)))
    (testing "correct member id"
      (is (= (:id (most-distal data clusts)) "p5")))
    (testing "correct distance"
      (is (< 
            (- (:dist (most-distal data clusts)) 1.37436854)
            0.0001)))))


(deftest uniqify-clusters-test
  (let [last-clusts [{:id 1 :members ["p1"] :center [1 1  1]}
                     {:id 2 :members ["p2"] :center [1 1  1]}
                     {:id 3 :members ["p3"] :center [1 0 -1]}]]
    (testing "correct size"
      (size-correct (uniqify-clusters last-clusts) 2))
    (testing "correct members"
      (is (some #{["p1" "p2"]} (map :members (uniqify-clusters last-clusts)))))))


(deftest merge-clusters-test
  (let [clst1 {:id 1 :center [1 1 1] :members ["a" "b"]}
        clst2 {:id 2 :center [1 0 0] :members ["c" "d"]}]
    (is (= #{"a" "b" "c" "d"}
           (set (:members (merge-clusters clst1 clst2)))))))


(let [last-clusters [{:members [1 2] :id 1}
                     {:members [3 4] :id 2}]
      nmat (fn [rows] (named-matrix rows [:x :y]
             [[1.2   0.4]
              [1.0   0.3]
              [-0.2 -0.4]
              [-0.7 -0.7]]))
      kmeanser (fn [new-data] (kmeans new-data 2 :last-clusters last-clusters))]

  (deftest missing-some-members
    (let [new-data (nmat [1 5 3 4])]
      (is (kmeanser new-data))
      (size-correct (kmeanser new-data) 2)))

  (deftest missing-all-members
    (let [new-data (nmat [6 5 3 4])]
      (is (kmeanser new-data))
      (size-correct (kmeanser new-data) 2)))

  (deftest missing-all-members-global
    (let [new-data (nmat [6 5 8 7])]
      (is (kmeanser new-data))
      (size-correct (kmeanser new-data) 2))))

(deftest missing-members
  (missing-some-members)
  (missing-all-members)
  (missing-all-members-global))


(deftest dropped-cluster
  ; Goal is to split clst 2 so that one mem goes to 1 and other goes to 3; empty cluster should handle
  (let [last-clusters [{:members [1 2] :id 1}
                       {:members [3 4] :id 2}
                       {:members [5 6] :id 3}]
        nmat (named-matrix [1 2 3 4 5 6] [:x :y]
               [[ 1.1  1.1]
                [ 1.0  1.0]
                [ 0.9  0.9]
                [-0.9 -0.9]
                [-1.0 -1.0]
                [-1.1 -1.1]])]
    (is (kmeans nmat 3 :last-clusters last-clusters))))



(def rand-gen
  (java.util.Random. 1234))


(defn random-vec
  [n]
  (for [_ (range n)]
    (.nextFloat rand-gen)))


(defn rand-int*
  [n]
  (.nextInt rand-gen n))

(defn dup-matrix-from-weights
  [mat weights]
  (reduce
    (fn [duped-mat [weight row]]
      (concat duped-mat (replicate weight row)))
    []
    (map vector weights mat)))

(defn dup-rownames-from-weights
  [rownames weights]
  (reduce
    (fn [duped-rownames [weight rowname]]
      (concat duped-rownames
              (for [i (range weight)]
                [rowname i])))
    []
    (map vector weights rownames)))

(defn setify-members
  [clsts & {:keys [trans] :or {trans identity}}]
  (->> clsts
       (map
         (fn [clst]
           (->>
             (:members clst)
             (map trans)
             (set))))
       (set)))

(defn print-mat [names mat]
  (doseq [[n r] (map vector names mat)]
    (println n ":" r)))


(deftest weighted-kmeans
  (doseq [_ (range 1)]
    (let [n-uniq       20
          n-dups-max   5
          n-cmnts      3
          uniq-positions   (for [_ (range n-uniq)] (random-vec n-cmnts))
          ptpt-names       (for [i (range n-uniq)] (str "p" i))
          weights          (for [_ (range n-uniq)] (inc (rand-int* n-dups-max)))
          duped-positions  (dup-matrix-from-weights uniq-positions weights)
          duped-names      (dup-rownames-from-weights ptpt-names weights)
          duped-data       (named-matrix duped-names [:x :y :z] duped-positions)
          deduped-data     (named-matrix ptpt-names [:x :y :z] uniq-positions)
          weighted-clsts   (kmeans deduped-data 3 :weights weights)
          unweighted-clsts (kmeans deduped-data 3)
          duped-clsts      (kmeans duped-data 3)]
      (is (= (setify-members weighted-clsts)
             (setify-members duped-clsts :trans first))))))

