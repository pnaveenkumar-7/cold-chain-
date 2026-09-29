// SPDX-License-Identifier: MIT
pragma solidity ^0.8.20;

/**
 * @title ColdChainLedger
 * @dev Immutable ledger for cold-chain sensor batch Merkle roots and tamper status.
 * Once written for a shipment ID, records cannot be modified or deleted.
 * Supports automated key rotation via transferOwnership(newAddress).
 */
contract ColdChainLedger {
    address public owner;

    struct ShipmentRecord {
        bytes32 merkleRoot;
        string dataRef;
        uint256 timestamp;
        bool tamperViolation;
    }

    // Mapping from shipment ID string to its immutable record
    mapping(string => ShipmentRecord) private records;

    // Events
    event RecordCommitted(
        string indexed shipmentId,
        bytes32 merkleRoot,
        string dataRef,
        uint256 timestamp,
        bool tamperViolation
    );

    event OwnershipTransferred(
        address indexed previousOwner,
        address indexed newOwner
    );

    modifier onlyOwner() {
        require(msg.sender == owner, "ColdChainLedger: Caller is not the owner");
        _;
    }

    constructor(address initialOwner) {
        address designatedOwner = initialOwner == address(0) ? msg.sender : initialOwner;
        owner = designatedOwner;
        emit OwnershipTransferred(address(0), designatedOwner);
    }

    /**
     * @notice Transfer authorization/ownership to a newly rotated KMS key address.
     * @dev Restricted to current owner. Does NOT alter or delete already-committed records.
     * @param newOwner Address derived from the rotated KMS public key
     */
    function transferOwnership(address newOwner) external onlyOwner {
        require(newOwner != address(0), "ColdChainLedger: New owner cannot be zero address");
        require(newOwner != owner, "ColdChainLedger: New owner must be different from current owner");

        address previousOwner = owner;
        owner = newOwner;
        emit OwnershipTransferred(previousOwner, newOwner);
    }

    /**
     * @notice Commit a new immutable batch record for a shipment.
     * @dev Restricted to owner. Enforces immutability by rejecting duplicate commits for the same shipment ID.
     * @param shipmentId Unique identifier for the shipment/crate
     * @param merkleRoot 32-byte Merkle tree root of validated sensor batch
     * @param dataRef URI or URL referencing raw sensor data
     * @param violationFlag True if hash-chain validation failed or temperature/tamper violation occurred
     */
    function commitRecord(
        string memory shipmentId,
        bytes32 merkleRoot,
        string memory dataRef,
        bool violationFlag
    ) external onlyOwner {
        require(bytes(shipmentId).length > 0, "ColdChainLedger: Shipment ID cannot be empty");
        require(merkleRoot != bytes32(0), "ColdChainLedger: Merkle root cannot be zero");
        require(records[shipmentId].timestamp == 0, "ColdChainLedger: Record already committed for this shipment");

        records[shipmentId] = ShipmentRecord({
            merkleRoot: merkleRoot,
            dataRef: dataRef,
            timestamp: block.timestamp,
            tamperViolation: violationFlag
        });

        emit RecordCommitted(shipmentId, merkleRoot, dataRef, block.timestamp, violationFlag);
    }

    /**
     * @notice Retrieve the immutable record for a given shipment ID.
     * @param shipmentId Unique identifier for the shipment
     * @return merkleRoot 32-byte Merkle root
     * @return dataRef URI/URL to raw sensor data
     * @return timestamp Block timestamp when the record was committed
     * @return tamperViolation Tamper/violation flag
     */
    function getRecord(string memory shipmentId)
        external
        view
        returns (
            bytes32 merkleRoot,
            string memory dataRef,
            uint256 timestamp,
            bool tamperViolation
        )
    {
        ShipmentRecord memory rec = records[shipmentId];
        require(rec.timestamp != 0, "ColdChainLedger: Record does not exist");
        return (rec.merkleRoot, rec.dataRef, rec.timestamp, rec.tamperViolation);
    }

    /**
     * @notice Check if a record exists for a shipment ID without reverting.
     */
    function recordExists(string memory shipmentId) external view returns (bool) {
        return records[shipmentId].timestamp != 0;
    }
}
