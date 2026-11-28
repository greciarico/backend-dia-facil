package dia_facil.backend.service.fotos;

import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Locale;
import javax.imageio.ImageIO;
import javax.imageio.stream.MemoryCacheImageInputStream;

final class FotoPerfil {
    static final int MAX_BYTES = 5 * 1024 * 1024;
    private static final long MAX_PIXELS = 24_000_000;
    private static final int SIZE = 512;

    static byte[] normalizar(byte[] input) {
        if (input == null || input.length == 0 || input.length > MAX_BYTES) throw invalida();
        try (var stream = new MemoryCacheImageInputStream(new ByteArrayInputStream(input))) {
            var readers = ImageIO.getImageReaders(stream);
            if (!readers.hasNext()) throw invalida();
            var reader = readers.next();
            try {
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!format.equals("jpeg") && !format.equals("png")) throw invalida();
                if (format.equals("png")) revisarPng(input);
                reader.setInput(stream, true, true);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width <= 0 || height <= 0 || (long) width * height > MAX_PIXELS) throw invalida();
                return jpeg(reader.read(0));
            } finally {
                reader.dispose();
            }
        } catch (IOException error) {
            throw invalida();
        }
    }

    private static void revisarPng(byte[] input) {
        var chunks = ByteBuffer.wrap(input).position(8);
        while (chunks.remaining() >= 12) {
            int length = chunks.getInt(), type = chunks.getInt();
            if (length < 0 || length > chunks.remaining() - 4 || type == 0x6163544c) throw invalida(); // acTL
            if (type == 0x49454e44 && length == 0 && chunks.remaining() == 4) return; // IEND
            chunks.position(chunks.position() + length + 4);
        }
        throw invalida();
    }

    private static byte[] jpeg(BufferedImage source) throws IOException {
        int edge = Math.min(source.getWidth(), source.getHeight());
        int x = (source.getWidth() - edge) / 2, y = (source.getHeight() - edge) / 2;
        var output = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_RGB);
        var graphics = output.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, SIZE, SIZE);
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.drawImage(source, 0, 0, SIZE, SIZE, x, y, x + edge, y + edge, null);
        } finally {
            graphics.dispose();
        }
        var bytes = new ByteArrayOutputStream();
        if (!ImageIO.write(output, "jpeg", bytes)) throw new IOException();
        return bytes.toByteArray();
    }

    private static IllegalArgumentException invalida() {
        return new IllegalArgumentException("Selecciona una foto JPEG o PNG estática de hasta 5 MiB y 24 megapíxeles.");
    }
}
