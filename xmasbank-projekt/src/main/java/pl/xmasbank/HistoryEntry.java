package pl.xmasbank;

/** Jeden wpis w historii transakcji. kind: "in", "out" lub "sys". */
public record HistoryEntry(long time, long amount, String kind, String other, String title) {
}
