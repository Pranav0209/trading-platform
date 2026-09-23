# Quick Start: 5-Minute Setup & Run

Follow these steps to set up the environment, run unit tests, load trade data, and verify the analytics warehouse.

---

## 1. Environment Setup

From the repository root:

```powershell
cd sprint-07-event-backbone\etl

# Create and activate Python virtual environment
python -m venv .venv
.venv\Scripts\activate

# Install the package in editable development mode
pip install -e ".[dev]"
```

---

## 2. Run Test Suite

Verify that data validation, date generation, dead-lettering, and idempotency logic pass:

```powershell
pytest tests/ -v
```

---

## 3. Run the ETL Pipeline

### Step A: Initialize the Analytical Warehouse
```powershell
trade-etl init-warehouse
```

### Step B: Run the Full Ingestion Pipeline
```powershell
trade-etl load-all --postgres-url "postgresql://postgres:postgres@localhost:5432/trading"
```

### Step C: Run Subsequent Incremental Batches
As new trades execute via the Trade API and Trade Executor, ingest only the delta:
```powershell
trade-etl load-trades --postgres-url "postgresql://postgres:postgres@localhost:5432/trading"
```

---

## 4. Run Warehouse Analytics & Health Checks

Verify data integrity and inspect analytical reports:

```powershell
trade-etl verify
```

Or run the script directly:
```powershell
python verify_warehouse.py
```
