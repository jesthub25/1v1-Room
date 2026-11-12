package org.jestron.arena1v1.model;

public enum MatchState {
    /** Oyuncular arenaya girdi, rakip bekleniyor. Kapilar acik. */
    WAITING,
    /** Dovus devam ediyor. Kapilar kapali. */
    FIGHTING,
    /** Mac bitti; kazanan item topluyor veya berabere bitis bekleniyor. Kapilar hala kapali. */
    ENDING
}
