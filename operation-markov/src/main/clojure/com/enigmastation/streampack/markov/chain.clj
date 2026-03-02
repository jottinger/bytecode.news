;; Markov chain text generation from a corpus of messages.
;; Pure data transformation: takes a list of strings, returns a generated sentence.

(ns com.enigmastation.streampack.markov.chain
  (:require [clojure.string :as str]))

(defn- tokenize
  "Splits a message into lowercase word tokens, discarding blanks."
  [text]
  (-> text str/lower-case str/trim (str/split #"\s+") (->> (remove str/blank?))))

(defn- build-chain
  "Builds a bigram transition map from a sequence of tokenized words.
   Each key maps to a vector of possible next words."
  [words]
  (->> (partition 2 1 words)
       (reduce (fn [chain [w1 w2]]
                 (update chain w1 (fnil conj []) w2))
               {})))

(defn- walk-chain
  "Walks the chain from a random starting word, producing up to max-words tokens."
  [chain max-words]
  (let [keys (vec (keys chain))
        start (rand-nth keys)]
    (loop [word start
           result [start]
           n 1]
      (if (or (>= n max-words)
              (nil? (get chain word)))
        (str/join " " result)
        (let [next-word (rand-nth (get chain word))]
          (recur next-word (conj result next-word) (inc n)))))))

(defn generate
  "Generates a sentence from a list of message strings.
   Returns nil if the corpus is too small to build a chain."
  [messages max-words]
  (let [words (mapcat tokenize messages)
        chain (build-chain words)]
    (when (seq chain)
      (walk-chain chain max-words))))
