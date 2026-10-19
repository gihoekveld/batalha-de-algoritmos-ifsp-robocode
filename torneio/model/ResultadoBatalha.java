package torneio.model;

public class ResultadoBatalha {
    public enum TipoVitoria {
        DESTRUICAO_KO("Destruição Total (K.O.)", ""),
        PRORROGACAO_HP("Vitória na Prorrogação por HP", ""),
        MORTE_SUBITA_HP("Morte Súbita no Primeiro Dano", ""),
        TEMPO_HP("Desempate por Energia (HP)", ""),
        TEMPO_DANO("Desempate por Dano Causado", ""),
        TEMPO_PONTUACAO("Desempate por Pontuação Geral", ""),
        PLACAR_REGULAMENTAR("Vitória por Placar no Tempo", "");

        public final String descricao;
        public final String icone;

        TipoVitoria(String descricao, String icone) {
            this.descricao = descricao;
            this.icone = icone;
        }
    }

    public Partida partida;
    public String nomeTimeA = "Time A";
    public String nomeTimeB = "Time B";
    public int placarFinalA = 0;
    public int placarFinalB = 0;

    public String vencedorNome = "";
    public String vencedorTag = ""; // "TIME_A" ou "TIME_B"
    public TipoVitoria tipoVitoria = TipoVitoria.DESTRUICAO_KO;
    public String tituloMotivo = "";
    public String explicacaoDetalhada = "";

    public double hpFinalA = 0;
    public double hpFinalB = 0;
    public double danoA = 0;
    public double danoB = 0;
    public double scoreRobocodeA = 0;
    public double scoreRobocodeB = 0;

    public boolean sincronizadoApi = false;
    public int roundsDisputados = 0;
    public int duracaoRealSegundos = 0;
}
