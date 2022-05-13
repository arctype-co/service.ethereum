(ns arctype.service.ethereum.rpc
  (:import
    [java.util UUID]
    [java.util.concurrent TimeoutException TimeUnit]
    [org.web3j.abi FunctionEncoder FunctionReturnDecoder TypeReference]
    [org.web3j.abi.datatypes Address Bool DynamicBytes]
    [org.web3j.abi.datatypes.generated Uint256])
  (:require
    [arctype.service.util :refer [<??]]
    [cheshire.core :as json]
    [clojure.core.async :as async]
    [clojure.tools.logging :as log]
    [clj-http.client :as http]
    [arctype.service.protocol.ethereum :refer [PEthereumContract]]))

(defn- get-abi-method
  [abi method]
  (if-let [abi-method (->>
                        abi
                        (filter
                          (fn [spec]
                            (= (name method)
                               (:name spec))))
                        (first))]
    abi-method
    (throw (ex-info "ABI method not found"
                    {:method method}))))

(defn- make-function
  [abi-method args]
  (FunctionEncoder/makeFunction
    (:name abi-method)
    (map :type (:inputs abi-method))
    args
    (map :type (:outputs abi-method))))

(defn- type-reference
  [output-type]
  (let [type-class
        (case (:type output-type)
          "address" Address
          "bool" Bool
          "bytes" DynamicBytes
          "uint256" Uint256
          (throw (ex-info "Undefined output type"
                          {:type output-type})))]
    (TypeReference/create type-class)))

(defn- type-value
  [web3-type]
  (.getValue web3-type))

(defn- decode-result
  [abi-method result]
  (let [output-params (map type-reference (:outputs abi-method))]
    (->>
      (FunctionReturnDecoder/decode 
        result
        output-params)
      (seq)
      (map type-value))))

(defn- build-transaction
  [this tx-params abi-method args]
  (let [func (make-function abi-method args)
        tx-data (FunctionEncoder/encode func)]
    (assoc tx-params :data tx-data)))

(def ^:private connect-timeout 10000)
(def ^:private socket-timeout 10000)

(defrecord EthereumRpcClient [url abi conn-mgr]
  PEthereumContract

  (call-tx
    [this tx-params block-id method args]
    (let [abi-method (get-abi-method abi method)
          id (str (UUID/randomUUID))
          transaction (build-transaction this tx-params abi-method args)]
      (log/trace {:message "Calling transaction"
                  :transaction transaction
                  :block-id block-id
                  :rpc-id id})
      (let [result (async/promise-chan)
            request (http/post
                      url
                      {:form-params 
                       {:jsonrpc "2.0"
                        :method "eth_call"
                        :params [transaction block-id]
                        :id id}
                       :async true
                       :connection-manager conn-mgr
                       :connect-timeout connect-timeout
                       :socket-timeout socket-timeout
                       :content-type :json
                       :as :json}
                      (fn [response] (async/put! result response))
                      (fn [exception] (async/put! result exception)))
            _ (try
                (.get request socket-timeout TimeUnit/MILLISECONDS)
                (catch TimeoutException e
                  (.cancel request true)
									(async/put! result e)))
            response (<?? result)]
        (if-let [error (get-in response [:body :error])]
          (throw (ex-info "JSON RPC error"
                          {:error error}))
          (if-let [result (get-in response [:body :result])]
            (decode-result abi-method result)
            (throw (ex-info "JSON RPC missing result"
                            {:response response}))))))))

(defn client
  [url abi-json]
  (->EthereumRpcClient url 
                       (json/parse-string abi-json true)
                       (clj-http.conn-mgr/make-reusable-async-conn-manager {:timeout 10 :threads 1})))
