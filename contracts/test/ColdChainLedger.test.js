const { expect } = require("chai");
const { ethers } = require("hardhat");

describe("ColdChainLedger Smart Contract", function () {
  let ColdChainLedger;
  let ledger;
  let owner;
  let addr1;
  let addr2;

  // Test data
  const shipmentId = "SHIP-1788602920826";
  const merkleRoot = "0x58427823570399988ac816d5556fd9dd5a118e81b3820e6ed79d698f0cd822a4";
  const dataRef = "http://localhost:3001/shipment/SHIP-1788602920826";
  const tamperViolation = false;

  beforeEach(async function () {
    [owner, addr1, addr2] = await ethers.getSigners();
    ColdChainLedger = await ethers.getContractFactory("ColdChainLedger");
    ledger = await ColdChainLedger.deploy();
    await ledger.waitForDeployment();
  });

  describe("Deployment", function () {
    it("Should set the deployer as the owner", async function () {
      expect(await ledger.owner()).to.equal(owner.address);
    });
  });

  describe("commitRecord & getRecord", function () {
    it("Should successfully commit a new shipment record and emit event", async function () {
      await expect(
        ledger.commitRecord(shipmentId, merkleRoot, dataRef, tamperViolation)
      )
        .to.emit(ledger, "RecordCommitted")
        .withArgs(
          shipmentId,
          merkleRoot,
          dataRef,
          (ts) => ts > 0,
          tamperViolation
        );

      // Verify record retrieval
      const record = await ledger.getRecord(shipmentId);
      expect(record.merkleRoot).to.equal(merkleRoot);
      expect(record.dataRef).to.equal(dataRef);
      expect(record.timestamp).to.be.gt(0);
      expect(record.tamperViolation).to.equal(false);
      expect(await ledger.recordExists(shipmentId)).to.equal(true);
    });

    it("Should store and retrieve a tampered shipment record accurately", async function () {
      const tamperedShipmentId = "SHIP-1788602944439";
      const tamperedRoot = "0xf61a8c1961ef951270e13bda785b93102379e29e32989135cc3bd6e2bbcf70b0";
      const tamperedRef = "http://localhost:3001/shipment/SHIP-1788602944439";

      await ledger.commitRecord(tamperedShipmentId, tamperedRoot, tamperedRef, true);

      const record = await ledger.getRecord(tamperedShipmentId);
      expect(record.merkleRoot).to.equal(tamperedRoot);
      expect(record.tamperViolation).to.equal(true);
    });

    it("IMMUTABILITY: Should REJECT a second commit to the same shipment ID", async function () {
      // First commit succeeds
      await ledger.commitRecord(shipmentId, merkleRoot, dataRef, tamperViolation);

      // Second commit to same ID must revert
      const newRoot = "0x1111111111111111111111111111111111111111111111111111111111111111";
      await expect(
        ledger.commitRecord(shipmentId, newRoot, "http://new-ref", true)
      ).to.be.revertedWith("ColdChainLedger: Record already committed for this shipment");

      // Verify original record is unchanged
      const record = await ledger.getRecord(shipmentId);
      expect(record.merkleRoot).to.equal(merkleRoot);
      expect(record.tamperViolation).to.equal(false);
    });

    it("ACCESS CONTROL: Should reject commits from non-owner accounts", async function () {
      await expect(
        ledger.connect(addr1).commitRecord(shipmentId, merkleRoot, dataRef, tamperViolation)
      ).to.be.revertedWith("ColdChainLedger: Caller is not the owner");
    });

    it("INPUT VALIDATION: Should reject empty shipmentId or zero merkle root", async function () {
      await expect(
        ledger.commitRecord("", merkleRoot, dataRef, false)
      ).to.be.revertedWith("ColdChainLedger: Shipment ID cannot be empty");

      await expect(
        ledger.commitRecord("SHIP-VALID", ethers.ZeroHash, dataRef, false)
      ).to.be.revertedWith("ColdChainLedger: Merkle root cannot be zero");
    });

    it("ERROR HANDLING: Should revert when querying non-existent shipment", async function () {
      await expect(
        ledger.getRecord("NON_EXISTENT_ID")
      ).to.be.revertedWith("ColdChainLedger: Record does not exist");

      expect(await ledger.recordExists("NON_EXISTENT_ID")).to.equal(false);
    });
  });
});
