package pt.studioflow.util;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class DataUtil {

    public static final DateTimeFormatter FORMATO_DATA = DateTimeFormatter.ofPattern("dd/MM/yyyy");
    public static final DateTimeFormatter FORMATO_DATA_HORA = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm");

    private DataUtil() {
    }

    public static String formatar(LocalDate data) {
        return data == null ? "" : data.format(FORMATO_DATA);
    }

    public static String formatar(LocalDateTime data) {
        return data == null ? "" : data.format(FORMATO_DATA_HORA);
    }
}
