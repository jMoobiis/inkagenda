package br.com.inkagenda.infra;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.Random;
import javax.imageio.ImageIO;

/** Gera os PNGs abstratos do seed: nenhuma imagem vem de terceiros. */
final class GeradorImagemAbstrata {

    private static final int LARGURA = 480;
    private static final int ALTURA = 600;
    private static final Color AMARELO = new Color(0xFF, 0xE0, 0x1B);
    private static final Color AZUL = new Color(0x25, 0x63, 0xEB);
    private static final Color OFF_WHITE = new Color(0xF8, 0xF7, 0xF4);
    private static final Color ESCURO = new Color(0x12, 0x12, 0x12);

    private GeradorImagemAbstrata() {
    }

    static byte[] png(long semente) {
        Random sorteio = new Random(semente);
        BufferedImage imagem = new BufferedImage(LARGURA, ALTURA, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = imagem.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        Color destaque = sorteio.nextBoolean() ? AMARELO : AZUL;
        g.setPaint(new GradientPaint(0, 0, ESCURO, LARGURA, ALTURA, new Color(0x2A, 0x2A, 0x2A)));
        g.fillRect(0, 0, LARGURA, ALTURA);
        for (int i = 0; i < 9; i++) {
            g.setColor(i % 3 == 0 ? destaque : OFF_WHITE);
            g.setStroke(new BasicStroke(2 + sorteio.nextInt(14)));
            int raio = 40 + sorteio.nextInt(220);
            g.drawOval(sorteio.nextInt(LARGURA) - raio / 2, sorteio.nextInt(ALTURA) - raio / 2, raio,
                    raio);
        }
        g.dispose();
        try (ByteArrayOutputStream saida = new ByteArrayOutputStream()) {
            ImageIO.write(imagem, "png", saida);
            return saida.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Falha ao gerar imagem do seed", e);
        }
    }
}
