package torneio.model;

public class Time {
    public Long id;
    public String nome;
    public String tipo;
    public String status;

    public Time() {}

    public Time(Long id, String nome) {
        this.id = id;
        this.nome = nome;
    }

    @Override
    public String toString() {
        return nome != null ? nome : "Time #" + id;
    }
}
