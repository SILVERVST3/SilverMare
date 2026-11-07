package com.silver.mare.engine;

/** Estação maregráfica com suas constantes harmônicas. Alturas em metros acima do nível de redução (NR). */
public final class Station {
    public final String id;
    public final String name;
    public final String uf;
    public final double latitude;
    public final double longitude;
    public final String timeZone;
    /** Nível médio acima do NR (Z0), em metros. */
    public final double meanLevel;
    /** Maior e menor maré astronômica previstas (acima do NR), em metros — usadas para escalar gráficos. */
    public final double highestAstronomical;
    public final double lowestAstronomical;
    public final String period;
    public final String source;
    final String[] names;
    final double[] amplitudes;
    final double[] phases;

    public Station(String id, String name, String uf, double latitude, double longitude, String timeZone,
                   double meanLevel, double highestAstronomical, double lowestAstronomical,
                   String period, String source, String[] names, double[] amplitudes, double[] phases) {
        this.id = id;
        this.name = name;
        this.uf = uf;
        this.latitude = latitude;
        this.longitude = longitude;
        this.timeZone = timeZone;
        this.meanLevel = meanLevel;
        this.highestAstronomical = highestAstronomical;
        this.lowestAstronomical = lowestAstronomical;
        this.period = period;
        this.source = source;
        this.names = names;
        this.amplitudes = amplitudes;
        this.phases = phases;
    }

    public String displayName() {
        return uf == null || uf.isEmpty() ? name : name + " (" + uf + ")";
    }

    /** Distância em km (haversine). */
    public double distanceKm(double lat, double lon) {
        double r = Math.PI / 180.0;
        double dLat = (lat - latitude) * r;
        double dLon = (lon - longitude) * r;
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(latitude * r) * Math.cos(lat * r) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * 6371.0 * Math.asin(Math.min(1.0, Math.sqrt(a)));
    }
}
