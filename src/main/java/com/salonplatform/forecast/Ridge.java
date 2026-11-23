package com.salonplatform.forecast;

/** Weighted ridge regression by Cholesky decomposition: small, dependency-free and fast enough to refit many times. */
final class Ridge {

    private Ridge() {}

    /**
     * Solves min Σ w_i (y_i - x_i·β)² + Σ λ_j β_j² and returns β.
     *
     * @param x      rows of features
     * @param y      targets
     * @param w      row weights
     * @param lambda per-column penalty (0 leaves a column unpenalised, such as the intercept)
     */
    static double[] solve(double[][] x, double[] y, double[] w, double[] lambda) {
        int n = x.length;
        int p = lambda.length;
        double[][] a = new double[p][p];
        double[] b = new double[p];
        for (int i = 0; i < n; i++) {
            double[] row = x[i];
            double wi = w[i];
            for (int j = 0; j < p; j++) {
                double xj = row[j];
                if (xj == 0) continue;
                b[j] += wi * xj * y[i];
                for (int k = 0; k <= j; k++) {
                    double xk = row[k];
                    if (xk != 0) a[j][k] += wi * xj * xk;
                }
            }
        }
        for (int j = 0; j < p; j++) {
            a[j][j] += lambda[j] + 1e-8;
            for (int k = j + 1; k < p; k++) a[j][k] = a[k][j];
        }
        return cholesky(a, b);
    }

    private static double[] cholesky(double[][] a, double[] b) {
        int p = b.length;
        double[][] l = new double[p][p];
        for (int i = 0; i < p; i++) {
            for (int j = 0; j <= i; j++) {
                double sum = a[i][j];
                for (int k = 0; k < j; k++) sum -= l[i][k] * l[j][k];
                if (i == j) {
                    if (sum <= 0) throw new IllegalStateException("Matrix is not positive definite");
                    l[i][i] = Math.sqrt(sum);
                } else {
                    l[i][j] = sum / l[j][j];
                }
            }
        }
        double[] z = new double[p];
        for (int i = 0; i < p; i++) {
            double s = b[i];
            for (int k = 0; k < i; k++) s -= l[i][k] * z[k];
            z[i] = s / l[i][i];
        }
        double[] beta = new double[p];
        for (int i = p - 1; i >= 0; i--) {
            double s = z[i];
            for (int k = i + 1; k < p; k++) s -= l[k][i] * beta[k];
            beta[i] = s / l[i][i];
        }
        return beta;
    }
}
