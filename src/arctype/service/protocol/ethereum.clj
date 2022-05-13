(ns arctype.service.protocol.ethereum)

(defprotocol PEthereumContract
  (call-tx 
    ;Call a transaction on a contract"
    [_ tx-params block-id method args]))

(defprotocol PEthereumClientService
  (rpc-client 
    ;Open a new RPC client for a specific contract ABI"
    [_ abi-json-file]))
