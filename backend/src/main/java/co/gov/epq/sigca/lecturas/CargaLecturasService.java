package co.gov.epq.sigca.lecturas;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import co.gov.epq.sigca.auditoria.Auditoria;
import co.gov.epq.sigca.auditoria.OperacionAuditoria;
import co.gov.epq.sigca.common.error.NegocioException;
import co.gov.epq.sigca.common.error.TraductorErroresOracle;
import co.gov.epq.sigca.lecturas.LecturaDtos.FilaResultado;
import co.gov.epq.sigca.lecturas.LecturaDtos.LecturaRegistrar;
import co.gov.epq.sigca.lecturas.LecturaDtos.ResultadoCarga;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

/**
 * Carga masiva de lecturas por CSV (P-07, LECTURA_CARGAR_ARCHIVO). Columnas: {@code serial,periodo,fecha,lectura,causa}
 * y, opcional, {@code observacion}; encabezado opcional; separador coma o punto y coma. Cada fila se valida y se
 * inserta en su propia transacción: una fila mala se reporta y no detiene el resto del archivo.
 */
@Service
public class CargaLecturasService {

    private static final Logger log = LoggerFactory.getLogger(CargaLecturasService.class);

    public static final long MAX_BYTES = 1024 * 1024;
    public static final int MAX_FILAS = 2000;
    private static final DateTimeFormatter DIA_MES_ANIO = DateTimeFormatter.ofPattern("dd/MM/uuuu");

    private final LecturaService lecturas;
    private final Validator validator;
    private final Auditoria auditoria;
    private final TransactionTemplate porFila;

    public CargaLecturasService(LecturaService lecturas, Validator validator, Auditoria auditoria,
            PlatformTransactionManager tm) {
        this.lecturas = lecturas;
        this.validator = validator;
        this.auditoria = auditoria;
        this.porFila = new TransactionTemplate(tm);
        this.porFila.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public ResultadoCarga cargar(MultipartFile archivo) {
        List<String> lineas = leer(archivo);
        String nombre = archivo.getOriginalFilename() == null ? "lecturas.csv" : archivo.getOriginalFilename();
        List<FilaResultado> filas = new ArrayList<>();
        char sep = lineas.stream().filter(s -> !s.isBlank()).findFirst().map(s -> s.contains(";") ? ';' : ',').orElse(',');
        int datos = 0;
        for (int i = 0; i < lineas.size(); i++) {
            String linea = lineas.get(i).strip();
            if (linea.isEmpty() || (datos == 0 && filas.isEmpty() && linea.toLowerCase(Locale.ROOT).startsWith("serial"))) {
                continue;
            }
            if (++datos > MAX_FILAS) {
                throw new NegocioException("ARCHIVO_INVALIDO", "El archivo supera las " + MAX_FILAS + " filas permitidas",
                        HttpStatus.BAD_REQUEST);
            }
            filas.add(procesar(i + 1, linea, sep));
        }
        if (filas.isEmpty()) {
            throw new NegocioException("ARCHIVO_INVALIDO", "El archivo no tiene filas de lecturas", HttpStatus.BAD_REQUEST);
        }
        ResultadoCarga r = resumen(nombre, filas);
        porFila.executeWithoutResult(s -> auditoria.exito(OperacionAuditoria.CARGAR_LECTURAS, LecturaService.ENTIDAD, null,
                Map.of("archivo", recortar(nombre, 100), "procesadas", r.procesadas(), "registradas", r.registradas(),
                        "enRevision", r.enRevision(), "conError", r.conError())));
        log.info("Carga de lecturas {}: {} registradas, {} con error", nombre, r.registradas(), r.conError());
        return r;
    }

    private FilaResultado procesar(int numero, String linea, char sep) {
        String[] c = linea.split(sep == ';' ? ";" : ",", -1);
        String serial = c.length > 0 ? c[0].strip().toUpperCase(Locale.ROOT) : "";
        Integer periodo = null;
        try {
            if (c.length < 5 || c.length > 6) {
                return error(numero, serial, null, "Se esperaban 5 columnas (serial,periodo,fecha,lectura,causa) y opcionalmente observacion");
            }
            periodo = entero(c[1], "periodo");
            LecturaRegistrar req = new LecturaRegistrar(serial, periodo, fecha(c[2]), decimal(c[3], sep), causa(c[4]),
                    c.length == 6 ? vacioANulo(c[5]) : null);
            String invalidos = validator.validate(req).stream()
                    .sorted(Comparator.comparing(v -> v.getPropertyPath().toString()))
                    .map(CargaLecturasService::mensaje).collect(Collectors.joining("; "));
            if (!invalidos.isEmpty()) {
                return error(numero, serial, periodo, invalidos);
            }
            Lectura l = porFila.execute(s -> lecturas.registrarFilaArchivo(req));
            return new FilaResultado(numero, serial, periodo, true, l.getId(), l.getEstado(), l.getConsumo(),
                    l.getEstado() == EstadoLectura.EN_REVISION ? "Consumo atípico: quedó en revisión" : "Registrada");
        } catch (NegocioException e) {
            return error(numero, serial, periodo, e.getMessage());
        } catch (RuntimeException e) {
            String msg = TraductorErroresOracle.traducir(e).map(Throwable::getMessage).orElse(null);
            if (msg == null) {
                log.warn("Fila {} del cargue de lecturas: error no esperado", numero, e);
                msg = "No fue posible registrar la fila";
            }
            return error(numero, serial, periodo, msg);
        }
    }

    private static List<String> leer(MultipartFile archivo) {
        if (archivo == null || archivo.isEmpty()) {
            throw new NegocioException("ARCHIVO_INVALIDO", "Adjunte un archivo CSV con lecturas", HttpStatus.BAD_REQUEST);
        }
        if (archivo.getSize() > MAX_BYTES) {
            throw new NegocioException("ARCHIVO_DEMASIADO_GRANDE", "El archivo supera 1 MB", HttpStatus.CONTENT_TOO_LARGE);
        }
        String nombre = archivo.getOriginalFilename();
        if (nombre != null && !nombre.toLowerCase(Locale.ROOT).endsWith(".csv") && !nombre.toLowerCase(Locale.ROOT).endsWith(".txt")) {
            throw new NegocioException("ARCHIVO_INVALIDO", "El archivo debe ser .csv", HttpStatus.BAD_REQUEST);
        }
        try {
            String texto = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(archivo.getBytes())).toString();
            if (texto.startsWith("﻿")) {
                texto = texto.substring(1);
            }
            if (texto.chars().anyMatch(ch -> ch == 0)) {
                throw new NegocioException("ARCHIVO_INVALIDO", "El archivo no es de texto", HttpStatus.BAD_REQUEST);
            }
            return texto.lines().toList();
        } catch (CharacterCodingException e) {
            throw new NegocioException("ARCHIVO_INVALIDO", "El archivo debe estar en UTF-8", HttpStatus.BAD_REQUEST);
        } catch (IOException e) {
            throw new NegocioException("ARCHIVO_INVALIDO", "No fue posible leer el archivo", HttpStatus.BAD_REQUEST);
        }
    }

    private static Integer entero(String s, String campo) {
        String v = s.strip();
        if (!v.matches("\\d{6}")) {
            throw new NegocioException("FILA_INVALIDA", "El " + campo + " debe tener formato AAAAMM");
        }
        return Integer.valueOf(v);
    }

    private static LocalDate fecha(String s) {
        String v = s.strip();
        try {
            return v.contains("/") ? LocalDate.parse(v, DIA_MES_ANIO) : LocalDate.parse(v);
        } catch (DateTimeParseException e) {
            throw new NegocioException("FILA_INVALIDA", "Fecha inválida: use AAAA-MM-DD o DD/MM/AAAA");
        }
    }

    private static BigDecimal decimal(String s, char sep) {
        String v = s.strip();
        if (v.isEmpty()) {
            return null;
        }
        if (sep == ';') {
            v = v.replace(',', '.');
        }
        if (!v.matches("\\d{1,9}(\\.\\d{1,3})?")) {
            throw new NegocioException("FILA_INVALIDA", "Lectura inválida: número de hasta 9 enteros y 3 decimales");
        }
        return new BigDecimal(v);
    }

    private static CausaNoLectura causa(String s) {
        String v = s.strip().toUpperCase(Locale.ROOT);
        if (v.isEmpty()) {
            return null;
        }
        try {
            return CausaNoLectura.valueOf(v);
        } catch (IllegalArgumentException e) {
            throw new NegocioException("FILA_INVALIDA", "Causa de no lectura inválida: use MEDIDOR_DANADO, PREDIO_CERRADO, "
                    + "ACCESO_IMPEDIDO, MEDIDOR_ILEGIBLE u OTRA");
        }
    }

    private static String vacioANulo(String s) {
        return s == null || s.isBlank() ? null : s.strip();
    }

    private static String mensaje(ConstraintViolation<?> v) {
        String campo = v.getPropertyPath().toString();
        return campo.isEmpty() ? v.getMessage() : campo + ": " + v.getMessage();
    }

    private static FilaResultado error(int linea, String serial, Integer periodo, String mensaje) {
        return new FilaResultado(linea, serial, periodo, false, null, null, null, mensaje);
    }

    private static ResultadoCarga resumen(String nombre, List<FilaResultado> filas) {
        int registradas = 0;
        int confirmadas = 0;
        int revision = 0;
        int estimadas = 0;
        for (FilaResultado f : filas) {
            if (!f.exito()) {
                continue;
            }
            registradas++;
            switch (f.estado()) {
                case CONFIRMADA -> confirmadas++;
                case EN_REVISION -> revision++;
                case ESTIMADA -> estimadas++;
                default -> { }
            }
        }
        return new ResultadoCarga(nombre, filas.size(), registradas, confirmadas, revision, estimadas, filas.size() - registradas, filas);
    }

    private static String recortar(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }
}
