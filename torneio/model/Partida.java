package torneio.model;

public class Partida {
    public Long id;
    public String fase;
    public String status;
    public Long torneioId;
    public Long timeAId;
    public Long timeBId;
    public String nomeTimeA = "Time A";
    public String nomeTimeB = "Time B";
    public Integer placarTimeA = 0;
    public Integer placarTimeB = 0;
    public Long vencedorId;
    public Long proximaPartidaId;

    @Override
    public String toString() {
        String statusStr = status != null ? status : "AGUARDANDO";
        String pA = placarTimeA != null ? placarTimeA.toString() : "0";
        String pB = placarTimeB != null ? placarTimeB.toString() : "0";
        String f = fase != null ? fase : "PARTIDA";
        return String.format("[#%d] %s: %s (%s) vs (%s) %s [%s]",
                id != null ? id : 0, f, nomeTimeA, pA, pB, nomeTimeB, statusStr);
    }
}
