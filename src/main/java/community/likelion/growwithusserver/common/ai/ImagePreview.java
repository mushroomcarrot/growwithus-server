package community.likelion.growwithusserver.common.ai;

import java.util.Locale;
import java.util.Set;

/** 사진을 가로 세로 최대 768픽셀의 JPEG로 줄인다. 읽을 수 없는 사진은 null이다. */
public final class ImagePreview {
    private ImagePreview() {}
    public static byte[] jpeg(byte[] image) throws java.io.IOException {
        try (var input = javax.imageio.ImageIO.createImageInputStream(new java.io.ByteArrayInputStream(image))) {
            var readers = javax.imageio.ImageIO.getImageReaders(input);
            if (!readers.hasNext()) return null;
            var reader = readers.next();
            try {
                String format = reader.getFormatName().toLowerCase(Locale.ROOT);
                if (!Set.of("jpeg", "jpg", "png").contains(format)) return null;
                reader.setInput(input, true, true);
                int width = reader.getWidth(0), height = reader.getHeight(0);
                if (width < 1 || height < 1 || width > 20000 || height > 20000
                        || (long) width * height > 50000000) return null;
                var parameters = reader.getDefaultReadParam();
                int scale = Math.max(1, (Math.max(width, height) + 767) / 768);
                parameters.setSourceSubsampling(scale, scale, 0, 0);
                var decoded = reader.read(0, parameters);
                var rgb = new java.awt.image.BufferedImage(decoded.getWidth(), decoded.getHeight(), java.awt.image.BufferedImage.TYPE_INT_RGB);
                var graphics = rgb.createGraphics();
                try {
                    graphics.setColor(java.awt.Color.WHITE); graphics.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
                    graphics.drawImage(decoded, 0, 0, null);
                } finally { graphics.dispose(); }
                var output = new java.io.ByteArrayOutputStream();
                return javax.imageio.ImageIO.write(rgb, "jpeg", output) ? output.toByteArray() : null;
            } finally { reader.dispose(); }
        }
    }
}
