(ns arctype.service.ethereum-test
  (:import
   [java.util UUID])
  (:require
   [arctype.service.protocol :refer :all]
   [arctype.service.protocol.health :refer [healthy?]]
   [arctype.service.ethereum :as ethereum]
   [arctype.test.resource :refer [*test-resource* mock-resource test-system-resource]]
   [arctype.test.util :refer [eventually*]]
   [clojure.test :refer :all]
   [sundbry.resource :as resource]))

(def test-config
  {:url "http://localhost:8545"})

(deftest ^:unit test-service-lifecycle
  (test-system-resource
   (ethereum/create :ethereum-rpc test-config)
   #{}
   (fn []
     (is (some? *test-resource*)))))
