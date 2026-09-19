package com.lab.nfe.sefaz;

/** tpAmb da NF-e: 1 = produção, 2 = homologação. */
public enum SefazAmbiente {
    PRODUCAO(1),
    HOMOLOGACAO(2);

    private final int tpAmb;

    SefazAmbiente(int tpAmb) {
        this.tpAmb = tpAmb;
    }

    public int tpAmb() {
        return tpAmb;
    }
}
