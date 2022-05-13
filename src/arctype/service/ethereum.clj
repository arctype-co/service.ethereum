(ns arctype.service.ethereum
  (:require
    [clojure.tools.logging :as log]
    [arctype.service.protocol.ethereum :refer [PEthereumClientService]]
    [arctype.service.ethereum.rpc :as rpc]
    [schema.core :as S]
    [sundbry.resource :as resource]))

(def Config
  {:url S/Str})

(defrecord EthereumRpcClientService
  [config]

  PEthereumClientService
  (rpc-client 
    [_ abi-json]
    (log/debug {:message "Opening Ethereum RPC"})
    (rpc/client (:url config) abi-json)))

(S/defn create
  [resource-name :- S/Keyword
   config :- Config]
  (resource/make-resource
    (->EthereumRpcClientService config)
    resource-name))
