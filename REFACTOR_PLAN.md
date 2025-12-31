# SmartDCA Refactor Plan - Futures Model

## Problem
Obecny model używa SPOT semantyki (wiele Position objects per dokup), ale dla FUTURES powinno być:
- JEDNA pozycja która rośnie/maleje
- Weighted average entry automatycznie przeliczany przez Portfolio
- Brak FIFO closing across multiple positions

## Zmiany

### 1. Infrastructure (DONE ✅)
- ✅ Portfolio.increasePosition() - dodaje do pozycji i rekalkuluje avgEntry
- ✅ TradingDecision.IncreasePosition - nowy typ decyzji
- ✅ BacktestEngine obsługuje IncreasePosition
- ✅ MockOrderExecutor.increasePosition()

### 2. SmartDCAAlgorithm (DONE ✅)
**Usunąć:**
- ✅ DCAPosition class (nie potrzebne - Position ma avgEntry)
- ✅ List<String> positionIds tracking
- ✅ FIFO closing logic (processPendingExit, initiatePendingExit)
- ✅ pendingExitQuantity state

**Zastąpić:**
- ✅ String currentPositionId (null = brak pozycji)
- ✅ PositionExitState exitState (dla tracking peak, stops, stages)
- ✅ executeBuy():
  - Jeśli currentPositionId == null → OpenPosition
  - Jeśli currentPositionId != null → IncreasePosition
- ✅ executeExit():
  - ClosePositionPartial/ClosePosition na currentPositionId
  - JEDEN call, nie ma FIFO!
- ✅ onPositionOpened():
  - currentPositionId = position.getId()
- ✅ onPositionClosed():
  - Jeśli full close → currentPositionId = null

### 3. ProfitManager (DONE ✅)
- ✅ Updated to work with Position + PositionExitState
- ✅ Uses position.getEntryPrice() (już weighted avg)

## Benefits
- 🎯 Zgodność z futures semantyką
- 🚀 ~200 linii kodu mniej
- 🐛 Brak desynchronizacji Portfolio ↔ DCAPosition
- ✅ Brak błędów floating point z FIFO closing
- 📊 Prostsze debugowanie
