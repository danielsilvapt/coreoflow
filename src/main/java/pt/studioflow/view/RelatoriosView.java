package pt.studioflow.view;

import com.vaadin.flow.server.StreamResource; // ou o pacote do Vaadin correspondente à sua versão
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.xssf.usermodel.XSSFColor;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.awt.Color;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.util.List;

import com.lowagie.text.*;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

import java.awt.BasicStroke;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import javax.imageio.ImageIO;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.ComponentEventListener;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import software.xdev.vaadin.chartjs.ChartContainer;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
import com.vaadin.flow.component.UI;
import com.vaadin.flow.component.grid.ColumnTextAlign;
import com.vaadin.flow.component.grid.Grid;
import com.vaadin.flow.component.grid.GridVariant;
import com.vaadin.flow.component.html.Anchor;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.component.html.H2;
import com.vaadin.flow.component.html.H3;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.component.icon.Icon;
import com.vaadin.flow.component.icon.VaadinIcon;
import com.vaadin.flow.component.notification.Notification;
import com.vaadin.flow.component.orderedlayout.HorizontalLayout;
import com.vaadin.flow.component.orderedlayout.VerticalLayout;
import com.vaadin.flow.router.PageTitle;
import com.vaadin.flow.router.Route;
import com.vaadin.flow.server.StreamResource;
import jakarta.annotation.security.RolesAllowed;
import pt.studioflow.config.TenantContext;
import pt.studioflow.model.*;
import pt.studioflow.repository.*;
import pt.studioflow.service.RemuneracaoService;
import pt.studioflow.util.WhatsAppUtil;

import java.awt.Color;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

import org.springframework.core.io.ClassPathResource;

@PageTitle("Relatórios | CoreoFlow")
@Route(value = "relatorios", layout = MainLayout.class)
@RolesAllowed("ADMIN")
public class RelatoriosView extends VerticalLayout {

        private final AlunoRepository alunoRepository;
        private final TurmaRepository turmaRepository;
        private final AlunoTurmaRepository alunoTurmaRepository;
        private final MensalidadeRepository mensalidadeRepository;
        private final RegistoHorasRepository registoHorasRepository;
        private final ProfessorRepository professorRepository;
        private final AulaRepository aulaRepository;
        private final RemuneracaoService remuneracaoService;
        private final pt.studioflow.repository.InterrupcaoLetivaRepository interrupcaoRepository;

        private final Color LARANJA_DANCE = new Color(255, 140, 0);
        private final DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy");
        // Interrupções letivas do estúdio, carregadas ao abrir o relatório de rentabilidade.
        private List<pt.studioflow.model.InterrupcaoLetiva> interrupcoesLetivas = new ArrayList<>();

        public RelatoriosView(AlunoRepository alunoRepository, TurmaRepository turmaRepository,
                        AlunoTurmaRepository alunoTurmaRepository, MensalidadeRepository mensalidadeRepository,
                        RegistoHorasRepository registoHorasRepository, ProfessorRepository professorRepository,
                        AulaRepository aulaRepository, RemuneracaoService remuneracaoService,
                        pt.studioflow.repository.InterrupcaoLetivaRepository interrupcaoRepository) {
                this.interrupcaoRepository = interrupcaoRepository;
                this.alunoRepository = alunoRepository;
                this.turmaRepository = turmaRepository;
                this.alunoTurmaRepository = alunoTurmaRepository;
                this.mensalidadeRepository = mensalidadeRepository;
                this.registoHorasRepository = registoHorasRepository;
                this.professorRepository = professorRepository;
                this.aulaRepository = aulaRepository;
                this.remuneracaoService = remuneracaoService;

                setSizeFull();
                setPadding(false);
                setSpacing(false);

                H2 titulo = new H2("Centro de Relatórios");
                titulo.getStyle().set("margin-top", "0");
                add(titulo);

                Div body = new Div();
                body.getStyle().set("padding", "24px").set("width", "100%");
                add(body);

                Div gridLayout = new Div();
                gridLayout.setWidthFull();
                gridLayout.getStyle().set("display", "grid")
                                .set("grid-template-columns", "repeat(auto-fill, minmax(320px, 1fr))")
                                .set("gap", "25px");

                gridLayout.add(criarCardRelatorio("Listagem de Dívidas",
                                "Cobranças pendentes com atalho para WhatsApp.",
                                VaadinIcon.MONEY, "#FF8C00", e -> abrirRelatorioDividas()));
                gridLayout.add(criarCardRelatorio("Pagamentos Profs",
                                "Cálculo de honorários e pedido de recibo por email.",
                                VaadinIcon.USER_CARD, "#FF8C00", e -> abrirRelatorioProfessores()));
                gridLayout.add(criarCardRelatorio("Rentabilidade Mensal",
                                "Lucro líquido detalhado e ordenado por turma.",
                                VaadinIcon.CHART_LINE, "#FF8C00", e -> abrirRelatorioRentabilidade()));
                gridLayout.add(criarCardRelatorio("Listas por Turma", "Alunos ativos, idades e presenças semanais.",
                                VaadinIcon.USERS, "#FF8C00", e -> abrirRelatorioAlunosPorTurma()));
                gridLayout.add(criarCardRelatorio("Seguros Associação", "Lista para envio mensal à seguradora.",
                                VaadinIcon.SHIELD, "#FF8C00", e -> abrirRelatorioSeguros()));

                body.add(gridLayout);
        }


        private Div criarCardRelatorio(String titulo, String desc, VaadinIcon icone, String cor,
                        ComponentEventListener<ClickEvent<Button>> listener) {
                Div card = new Div();
                card.getStyle().set("background", "white").set("padding", "20px").set("border-radius", "12px").set(
                                "box-shadow",
                                "0 4px 10px rgba(0,0,0,0.05)");
                Icon icon = icone.create();
                icon.setColor(cor);
                icon.setSize("40px");
                Button btn = new Button("Visualizar / PDF", listener);
                btn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
                btn.getStyle().set("background-color", cor);
                VerticalLayout content = new VerticalLayout(icon, new H3(titulo), new Span(desc), btn);
                content.setPadding(false);
                card.add(content);
                return card;
        }

        // --- HELPERS DE PERÍODO / REMUNERAÇÃO ---

        private String fmtEuro(double v) {
                return String.format("%.2f €", v);
        }

        private String mesLabel(YearMonth m) {
                return m.getMonth().getDisplayName(TextStyle.FULL, new Locale("pt")) + " " + m.getYear();
        }

        private ComboBox<YearMonth> criarSeletorMes(YearMonth inicial) {
                ComboBox<YearMonth> cb = new ComboBox<>("Mês");
                YearMonth base = YearMonth.now();
                List<YearMonth> opcoes = new ArrayList<>();
                for (int i = -24; i <= 12; i++) opcoes.add(base.plusMonths(i));
                cb.setItems(opcoes);
                cb.setItemLabelGenerator(this::mesLabel);
                cb.setAllowCustomValue(false);
                cb.setWidth("240px");
                cb.setValue(inicial);
                return cb;
        }

        private RemuneracaoService.Dados carregarDadosRemuneracao(Studio studio, List<Turma> turmas) {
                java.util.Set<Long> turmaIds = turmas.stream().map(Turma::getId).collect(Collectors.toSet());
                List<AlunoTurma> inscricoes = alunoTurmaRepository.findAll().stream()
                                .filter(at -> at.getTurma() != null && turmaIds.contains(at.getTurma().getId()))
                                .collect(Collectors.toList());
                return new RemuneracaoService.Dados()
                                .registos(studio != null ? registoHorasRepository.findAllByStudio(studio)
                                                : registoHorasRepository.findAll())
                                .mensalidades(studio != null ? mensalidadeRepository.findAllByStudio(studio)
                                                : mensalidadeRepository.findAll())
                                .inscricoes(inscricoes)
                                .aulas(studio != null ? aulaRepository.findByTurmaStudio(studio)
                                                : aulaRepository.findAll());
        }

        private HorizontalLayout linhaDownloads(String titulo, String[] headers, List<String[]> rows) {
                Anchor pdf = new Anchor(gerarPDFGenerico(titulo, LARANJA_DANCE, headers, rows), "");
                pdf.getElement().setAttribute("download", true);
                Button btnPdf = new Button("Download PDF", VaadinIcon.DOWNLOAD.create());
                btnPdf.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
                btnPdf.getStyle().set("background-color", "#FF8C00");
                pdf.add(btnPdf);

                Anchor xls = new Anchor(gerarExcelGenerico(titulo, LARANJA_DANCE, headers, rows), "");
                xls.getElement().setAttribute("download", true);
                Button btnXls = new Button("Download Excel", VaadinIcon.DOWNLOAD.create());
                btnXls.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
                btnXls.getStyle().set("background-color", "#FF8C00");
                xls.add(btnXls);

                return new HorizontalLayout(pdf, xls);
        }

        // --- 1. RELATÓRIO PAGAMENTOS A PROFESSORES (com seletor de mês) ---
        private void abrirRelatorioProfessores() {
                Studio studio = TenantContext.getCurrentStudio();
                List<Professor> professores = studio != null ? professorRepository.findAllByStudio(studio)
                                : professorRepository.findAll();
                List<Turma> turmas = studio != null ? turmaRepository.findAllByStudio(studio)
                                : turmaRepository.findAll();
                RemuneracaoService.Dados dados = carregarDadosRemuneracao(studio, turmas);

                Dialog d = new Dialog();
                d.setHeaderTitle("Pagamentos a Professores");
                d.setWidth("950px");
                d.setHeight("650px");

                ComboBox<YearMonth> seletor = criarSeletorMes(YearMonth.now().minusMonths(1));
                Div container = new Div();
                container.setWidthFull();
                container.getStyle().set("flex-grow", "1").set("overflow", "auto");

                Runnable render = () -> {
                        container.removeAll();
                        YearMonth mes = seletor.getValue();
                        String nomeMes = mesLabel(mes);
                        boolean previsto = remuneracaoService.ehFuturo(mes);
                        List<RemuneracaoService.LinhaPagamento> linhas = remuneracaoService
                                        .pagamentosPorProfessor(professores, turmas, studio, mes, dados);

                        Grid<RemuneracaoService.LinhaPagamento> grid = new Grid<>();
                        grid.setItems(linhas);
                        grid.addComponentColumn(l -> botaoEmailPagamento(l, nomeMes)).setHeader("E-mail").setAutoWidth(true);
                        grid.addColumn(RemuneracaoService.LinhaPagamento::nome).setHeader("Professor");
                        grid.addColumn(l -> l.modo() == TipoRemuneracao.PERCENTAGEM ? "% mensalidade" : "€/hora")
                                        .setHeader("Modo").setAutoWidth(true);
                        grid.addColumn(l -> fmtEuro(l.base()))
                                        .setHeader("Aulas/Base").setAutoWidth(true);
                        grid.addColumn(l -> fmtEuro(l.ensaios())).setHeader("Ensaios").setAutoWidth(true);
                        grid.addColumn(l -> fmtEuro(l.privadas())).setHeader("Privadas/WS").setAutoWidth(true);
                        grid.addColumn(l -> fmtEuro(l.total())).setHeader("Total");
                        grid.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_ROW_STRIPES);
                        grid.setSizeFull();

                        String[] headers = { "Professor", "Modo", "Aulas/Base", "Ensaios", "Privadas/WS", "Total" };
                        List<String[]> rows = linhas.stream()
                                        .map(l -> new String[] { l.nome(),
                                                        l.modo() == TipoRemuneracao.PERCENTAGEM ? "% mensalidade" : "€/hora",
                                                        fmtEuro(l.base()), fmtEuro(l.ensaios()), fmtEuro(l.privadas()),
                                                        fmtEuro(l.total()) })
                                        .collect(Collectors.toList());
                        double totalGeral = linhas.stream().mapToDouble(RemuneracaoService.LinhaPagamento::total).sum();
                        rows.add(new String[] { "TOTAL", "", "", "", "", fmtEuro(totalGeral) });

                        String tituloExport = "Pagamentos Profs - " + nomeMes + (previsto ? " (previsao)" : "");

                        Span aviso = new Span(previsto
                                        ? "⚠️ Mês futuro — estimativa a partir das inscrições ativas e das aulas agendadas."
                                        : "Valores reais do mês selecionado (registos de horas + mensalidades).");
                        aviso.getStyle().set("font-size", "12px").set("color", previsto ? "#e65100" : "#888");

                        VerticalLayout v = new VerticalLayout(aviso, grid, linhaDownloads(tituloExport, headers, rows));
                        v.setSizeFull();
                        v.setPadding(false);
                        v.expand(grid);
                        container.add(v);
                };
                seletor.addValueChangeListener(e -> render.run());
                render.run();

                VerticalLayout wrap = new VerticalLayout(seletor, container);
                wrap.setSizeFull();
                wrap.expand(container);
                d.add(wrap);
                d.getFooter().add(new Button("Fechar", e -> d.close()));
                d.open();
        }

        private Component botaoEmailPagamento(RemuneracaoService.LinhaPagamento l, String nomeMes) {
                Button btnEmail = new Button(VaadinIcon.ENVELOPE.create());
                btnEmail.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);

                String emailProf = l.professor() != null && l.professor().getEmail() != null
                                ? l.professor().getEmail() : "";
                if (emailProf.isBlank()) {
                        btnEmail.setEnabled(false);
                        btnEmail.getElement().setAttribute("title", "Professor sem email definido");
                        return btnEmail;
                }

                String pNome = l.nome().split(" ")[0];
                String valor = String.format("%.2f", l.total());
                String assunto = "Pagamento de Aulas - " + nomeMes;
                String corpo = String.format(
                                "Olá %s,\n\nA fim de podermos efetuar a transferência bancária referente às aulas do mês de %s, solicitamos o envio do recibo no valor de %s€.\n\nObrigada,\n\n---\n*** Mensagem enviada a partir da Plataforma CoreoFlow ***",
                                pNome, nomeMes, valor);
                String mailto = "mailto:" + emailProf + "?subject="
                                + URLEncoder.encode(assunto, StandardCharsets.UTF_8).replace("+", "%20")
                                + "&body="
                                + URLEncoder.encode(corpo, StandardCharsets.UTF_8).replace("+", "%20");

                // Âncora mailto nativa: o browser abre o cliente de email sem navegar a SPA
                // para fora (o open(mailto, "_self") anterior partia a ligação e deixava a
                // modal presa, impossível de fechar).
                Anchor link = new Anchor(mailto, btnEmail);
                link.setTarget("_blank");
                link.getElement().setAttribute("router-ignore", true);
                return link;
        }

        // --- 2. RELATÓRIO ALUNOS POR TURMA (ESPAÇAMENTO CORRIGIDO) ---
        private void abrirRelatorioAlunosPorTurma() {
                Grid<AlunoTurma> grid = new Grid<>();
                pt.studioflow.model.Studio _sRel1 = pt.studioflow.config.TenantContext.getCurrentStudio();
                grid.setItems(alunoTurmaRepository.findAll().stream()
                                .filter(at -> at.getAluno().isAtivo())
                                .filter(at -> _sRel1 == null || (at.getTurma().getStudio() != null && at.getTurma().getStudio().getId().equals(_sRel1.getId())))
                                .collect(Collectors.toList()));
                grid.addColumn(at -> at.getTurma().getDescricao()).setHeader("Turma").setSortable(true);
                grid.addColumn(at -> formatarNome(at.getAluno().getNomeCompleto())).setHeader("Aluno");
                grid.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_ROW_STRIPES);

                Anchor anchor = new Anchor(new StreamResource("Lista_Alunos_Turmas.pdf", () -> {
                        try {
                                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                                Document doc = new Document(PageSize.A4);
                                PdfWriter.getInstance(doc, baos);
                                doc.open();
                                adicionarLogo(doc);

                                pt.studioflow.model.Studio _sRel2 = pt.studioflow.config.TenantContext.getCurrentStudio();
                                java.util.List<Turma> _turmasRel2 = _sRel2 != null ? turmaRepository.findAllByStudio(_sRel2) : turmaRepository.findAll();
                                for (Turma t : _turmasRel2) {
                                        List<AlunoTurma> insc = alunoTurmaRepository.findByTurma(t).stream()
                                                        .filter(at -> at.getAluno().isAtivo())
                                                        .collect(Collectors.toList());
                                        if (insc.isEmpty())
                                                continue;

                                        Paragraph pt = new Paragraph("Turma: " + t.getDescricao(),
                                                        FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14));
                                        pt.setSpacingBefore(25); // Espaço antes do título
                                        pt.setSpacingAfter(10); // Espaço depois do título (antes da tabela)
                                        doc.add(pt);

                                        PdfPTable tab = new PdfPTable(4);
                                        tab.setWidthPercentage(100);
                                        String[] h = { "Nome", "Idade", "Telemóvel", "V/Sem" };
                                        for (String s : h) {
                                                PdfPCell c = new PdfPCell(
                                                                new Phrase(s, FontFactory.getFont(
                                                                                FontFactory.HELVETICA_BOLD, 10,
                                                                                Color.WHITE)));
                                                c.setBackgroundColor(LARANJA_DANCE);
                                                c.setPadding(6);
                                                tab.addCell(c);
                                        }
                                        for (AlunoTurma at : insc) {
                                                tab.addCell(new Phrase(formatarNome(at.getAluno().getNomeCompleto()),
                                                                FontFactory.getFont(FontFactory.HELVETICA, 9)));
                                                tab.addCell(new Phrase(calcularIdade(at.getAluno().getDataNascimento()),
                                                                FontFactory.getFont(FontFactory.HELVETICA, 9)));
                                                tab.addCell(
                                                                new Phrase(at.getAluno().getTelemovel() != null
                                                                                ? at.getAluno().getTelemovel()
                                                                                : "-",
                                                                                FontFactory.getFont(
                                                                                                FontFactory.HELVETICA,
                                                                                                9)));
                                                tab.addCell(new Phrase(String.valueOf(at.getAulasPorSemana()),
                                                                FontFactory.getFont(FontFactory.HELVETICA, 9)));
                                        }
                                        doc.add(tab);
                                }
                                doc.close();
                                return new ByteArrayInputStream(baos.toByteArray());
                        } catch (Exception ex) {
                                return null;
                        }
                }), "");
                anchor.getElement().setAttribute("download", true);
                Button btnPdf = new Button("Download PDF", VaadinIcon.DOWNLOAD.create());
                btnPdf.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
                btnPdf.getStyle().set("background-color", "#FF8C00");
                anchor.add(btnPdf);

                Dialog d = new Dialog();
                d.setHeaderTitle("Listas de Alunos por Turma");
                d.setWidth("950px");
                d.setHeight("650px");
                VerticalLayout v = new VerticalLayout(grid, anchor);
                v.setSizeFull();
                v.expand(grid);
                d.add(v);
                d.getFooter().add(new Button("Fechar", e -> d.close()));
                d.open();
        }

        // --- MÉTODOS DE APOIO ---

        private void configurarDialogComGrid(String titulo, Grid grid, List<String[]> rows, String[] headers) {
                Dialog d = new Dialog();
                d.setHeaderTitle(titulo);
                d.setWidth("950px");
                d.setHeight("650px");
                grid.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_ROW_STRIPES);

                Anchor anchor = new Anchor(gerarPDFGenerico(titulo, LARANJA_DANCE, headers, rows), "");
                anchor.getElement().setAttribute("download", true);
                Button btn = new Button("Download PDF", VaadinIcon.DOWNLOAD.create());
                btn.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
                btn.getStyle().set("background-color", "#FF8C00");
                anchor.add(btn);

                Anchor anchor2 = new Anchor(gerarExcelGenerico(titulo, LARANJA_DANCE, headers, rows), "");
                anchor2.getElement().setAttribute("download", true);
                Button btn2 = new Button("Download Excel", VaadinIcon.DOWNLOAD.create());
                btn2.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
                btn2.getStyle().set("background-color", "#FF8C00");
                anchor2.add(btn2);

                VerticalLayout v = new VerticalLayout(grid, anchor, anchor2);
                v.setSizeFull();
                v.expand(grid);
                d.add(v);
                d.getFooter().add(new Button("Fechar", e -> d.close()));
                d.open();
        }

        private void adicionarLogo(Document doc) throws Exception {
                try {
                        // Busca o ficheiro dentro da pasta resources
                        ClassPathResource res = new ClassPathResource("static/images/logo-studio.png");
                        byte[] bytes = res.getInputStream().readAllBytes();

                        Image logo = Image.getInstance(bytes);
                        logo.scaleToFit(80, 80);
                        logo.setAlignment(Element.ALIGN_CENTER);
                        logo.setSpacingAfter(15);
                        doc.add(logo);
                } catch (Exception e) {
                        // Seu fallback para texto caso a imagem falhe
                        System.out.println("Logo não encontrada, usando fallback de texto.");
                }
        }

        private StreamResource gerarPDFGenerico(String titulo, Color color, String[] h, List<String[]> rows) {
                return new StreamResource(titulo.replace(" ", "_") + ".pdf", () -> {
                        try {
                                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                                Document doc = new Document(PageSize.A4);
                                PdfWriter.getInstance(doc, baos);
                                doc.open();
                                adicionarLogo(doc);

                                Paragraph t = new Paragraph(titulo,
                                                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16));
                                t.setAlignment(Element.ALIGN_CENTER);
                                t.setSpacingAfter(20);
                                doc.add(t);

                                PdfPTable tab = new PdfPTable(h.length);
                                tab.setWidthPercentage(100);
                                for (String s : h) {
                                        PdfPCell c = new PdfPCell(
                                                        new Phrase(s, FontFactory.getFont(FontFactory.HELVETICA_BOLD,
                                                                        10, Color.WHITE)));
                                        c.setBackgroundColor(color);
                                        c.setPadding(6);
                                        tab.addCell(c);
                                }
                                for (String[] r : rows) {
                                        for (String cell : r) {
                                                PdfPCell c = new PdfPCell(new Phrase(cell,
                                                                FontFactory.getFont(FontFactory.HELVETICA, 9)));
                                                c.setPadding(5);
                                                tab.addCell(c);
                                        }
                                }
                                doc.add(tab);

                                Paragraph rodape = new Paragraph("\nGerado em: " + LocalDate.now().format(fmt),
                                                FontFactory.getFont(FontFactory.HELVETICA, 8, Font.COLOR_NORMAL, Color.GRAY));
                                rodape.setAlignment(Element.ALIGN_RIGHT);
                                doc.add(rodape);

                                doc.close();
                                return new ByteArrayInputStream(baos.toByteArray());
                        } catch (Exception e) {
                                return null;
                        }
                });
        }

        private StreamResource gerarExcelGenerico(String titulo, Color color, String[] h, List<String[]> rows) {
                return new StreamResource(titulo.replace(" ", "_") + ".xlsx", () -> {
                        try (Workbook workbook = new XSSFWorkbook();
                                        ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

                                // Cria a folha com o nome do título (limitado a 31 caracteres exigidos pelo
                                // Excel)
                                String sheetName = titulo.length() > 30 ? titulo.substring(0, 30) : titulo;
                                Sheet sheet = workbook.createSheet(sheetName);

                                int currentRowNum = 0;

                                // 1. Estilo do Título Principal
                                Row titleRow = sheet.createRow(currentRowNum++);
                                Cell titleCell = titleRow.createCell(0);
                                titleCell.setCellValue(titulo);
                                CellStyle titleStyle = workbook.createCellStyle();
                                Font titleFont = workbook.createFont();
                                titleFont.setFontName("Helvetica");
                                titleFont.setFontHeightInPoints((short) 16);
                                titleFont.setBold(true);
                                titleStyle.setFont(titleFont);
                                titleCell.setCellStyle(titleStyle);

                                // Linha em branco de espaçamento (equivalente ao setSpacingAfter)
                                currentRowNum++;

                                // 2. Estilo do Cabeçalho (Header) com a cor dinâmica recebida por parâmetro
                                Row headerRow = sheet.createRow(currentRowNum++);
                                CellStyle headerStyle = workbook.createCellStyle();

                                // Configura a cor de fundo personalizada do cabeçalho
                                if (headerStyle instanceof org.apache.poi.xssf.usermodel.XSSFCellStyle) {
                                        org.apache.poi.xssf.usermodel.XSSFCellStyle xssfHeaderStyle = (org.apache.poi.xssf.usermodel.XSSFCellStyle) headerStyle;
                                        xssfHeaderStyle.setFillForegroundColor(new XSSFColor(color, null));
                                        xssfHeaderStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
                                }

                                // Fonte Branca e Negrito para o Cabeçalho
                                Font headerFont = workbook.createFont();
                                headerFont.setFontName("Helvetica");
                                headerFont.setFontHeightInPoints((short) 10);
                                headerFont.setBold(true);
                                headerFont.setColor(IndexedColors.WHITE.getIndex());
                                headerStyle.setFont(headerFont);

                                // Borda fina para o cabeçalho
                                headerStyle.setBorderBottom(BorderStyle.THIN);
                                headerStyle.setBorderTop(BorderStyle.THIN);
                                headerStyle.setBorderLeft(BorderStyle.THIN);
                                headerStyle.setBorderRight(BorderStyle.THIN);

                                // Escrever os cabeçalhos
                                for (int i = 0; i < h.length; i++) {
                                        Cell cell = headerRow.createCell(i);
                                        cell.setCellValue(h[i]);
                                        cell.setCellStyle(headerStyle);
                                }

                                // 3. Estilo das Linhas de Dados (Rows)
                                CellStyle rowStyle = workbook.createCellStyle();
                                Font rowFont = workbook.createFont();
                                rowFont.setFontName("Helvetica");
                                rowFont.setFontHeightInPoints((short) 9);
                                rowStyle.setFont(rowFont);
                                rowStyle.setBorderBottom(BorderStyle.THIN);
                                rowStyle.setBorderTop(BorderStyle.THIN);
                                rowStyle.setBorderLeft(BorderStyle.THIN);
                                rowStyle.setBorderRight(BorderStyle.THIN);

                                // Escrever os dados
                                for (String[] r : rows) {
                                        Row row = sheet.createRow(currentRowNum++);
                                        for (int i = 0; i < r.length; i++) {
                                                Cell cell = row.createCell(i);
                                                cell.setCellValue(r[i] != null ? r[i] : "");
                                                cell.setCellStyle(rowStyle);
                                        }
                                }

                                // Linha em branco antes do rodapé
                                currentRowNum++;

                                // 4. Rodapé (Data de geração)
                                Row footerRow = sheet.createRow(currentRowNum);
                                Cell footerCell = footerRow.createCell(Math.max(0, h.length - 1)); // Alinha à direita
                                                                                                   // na última coluna
                                footerCell.setCellValue("Gerado em: " + LocalDate.now().format(fmt)); // Utiliza o seu
                                                                                                      // 'fmt' existente

                                CellStyle footerStyle = workbook.createCellStyle();
                                Font footerFont = workbook.createFont();
                                footerFont.setFontName("Helvetica");
                                footerFont.setFontHeightInPoints((short) 8);
                                footerFont.setItalic(true);
                                footerFont.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
                                footerStyle.setFont(footerFont);
                                footerStyle.setAlignment(HorizontalAlignment.RIGHT);
                                footerCell.setCellStyle(footerStyle);

                                // Auto-ajustar a largura das colunas conforme o conteúdo
                                for (int i = 0; i < h.length; i++) {
                                        sheet.autoSizeColumn(i);
                                }

                                // Escreve os bytes na memória
                                workbook.write(baos);
                                return new ByteArrayInputStream(baos.toByteArray());

                        } catch (Exception e) {
                                e.printStackTrace(); // Boa prática para conseguir debugar se algo falhar
                                return null;
                        }
                });
        }

        // --- OUTROS MÉTODOS ---

        private void abrirRelatorioDividas() {
                List<Map<String, Object>> dados = obterDadosDevedores();
                Grid<Map<String, Object>> grid = new Grid<>();
                grid.setItems(dados);
                grid.addComponentColumn(m -> {
                        Button b = new Button(VaadinIcon.CHAT.create(), e -> {
                                String pNome = m.get("nome").toString().split(" ")[0];
                                pt.studioflow.model.Studio _stCob = TenantContext.getCurrentStudio();
                                String nomeEsc = (_stCob != null && _stCob.getNome() != null
                                                && !_stCob.getNome().isBlank()) ? _stCob.getNome() : "escola";
                                String msg = "Olá " + pNome
                                                + "! Notamos que a mensalidade da " + nomeEsc + " está pendente ("
                                                + String.format("%.2f", (Double) m.get("total"))
                                                + "€). Pedimos que regularize. Obrigado!";
                                getUI().ifPresent(ui -> ui.getPage()
                                                .open(WhatsAppUtil.gerarLinkMensagem(m.get("telemovel").toString(),
                                                                msg), "_blank"));
                        });
                        b.getStyle().set("color", "#25D366");
                        b.addThemeVariants(ButtonVariant.LUMO_SUCCESS, ButtonVariant.LUMO_SMALL);
                        return b;
                }).setHeader("WhatsApp");
                grid.addColumn(m -> m.get("nome")).setHeader("Aluno");
                grid.addColumn(m -> String.format("%.2f €", m.get("total"))).setHeader("Total");
                List<String[]> rows = dados.stream()
                                .map(m -> new String[] { m.get("nome").toString(), m.get("telemovel").toString(),
                                                m.get("email").toString(), String.format("%.2f €", m.get("total")) })
                                .collect(Collectors.toList());
                configurarDialogComGrid("Gestão de Dívidas", grid, rows,
                                new String[] { "Aluno", "Telemóvel", "Email", "Total" });
        }

        // --- 3. RELATÓRIO RENTABILIDADE MENSAL (com seletor de mês) ---
        private void abrirRelatorioRentabilidade() {
                Studio studio = TenantContext.getCurrentStudio();
                interrupcoesLetivas = studio != null
                                ? interrupcaoRepository.findByStudioOrderByDataInicioAsc(studio)
                                : new ArrayList<>();
                List<Turma> todasTurmas = studio != null ? turmaRepository.findAllByStudio(studio)
                                : turmaRepository.findAll();
                RemuneracaoService.Dados dados = carregarDadosRemuneracao(studio, todasTurmas);
                // Só turmas com aulas regulares planeadas no mapa de salas.
                List<Turma> turmas = todasTurmas.stream()
                                .filter(t -> temAulasRegularesPlaneadas(t, dados.aulas))
                                .collect(Collectors.toList());

                Dialog d = new Dialog();
                d.setHeaderTitle("Rentabilidade Mensal");
                dialogRedimensionavel(d, "1000px", "800px");

                ComboBox<YearMonth> seletor = criarSeletorMes(YearMonth.now().minusMonths(1));
                Div container = new Div();
                container.setWidthFull();
                container.getStyle().set("flex-grow", "1").set("overflow", "auto");

                UI.getCurrent().getElement().executeJs(
                                "if(!document.getElementById('rent-styles')){"
                                + "const s=document.createElement('style');s.id='rent-styles';s.textContent=$0;"
                                + "document.head.appendChild(s);}",
                                "vaadin-grid::part(rent-real){background-color:#FFF3E0 !important;}"
                                + "vaadin-grid::part(rent-est){background-color:#FFFDE7 !important;}"
                                + "vaadin-grid::part(rent-pos){color:#2E7D32 !important;font-weight:700;}"
                                + "vaadin-grid::part(rent-neg){color:#C62828 !important;font-weight:700;}"
                                + "vaadin-grid::part(rent-grp){font-weight:700;border-top:1px solid #cfd8dc;}"
                                + "vaadin-grid::part(rent-sep){border-right:2px solid #b0bec5 !important;}");

                Runnable render = () -> {
                        container.removeAll();
                        YearMonth mes = seletor.getValue();
                        boolean previsto = remuneracaoService.ehFuturo(mes);
                        Map<Long, double[]> rent = remuneracaoService.rentabilidadeDetalhadaPorTurma(turmas, studio,
                                        mes, dados);

                        // Fração real do mês por turma (datas das aulas no mapa de salas),
                        // com o peso do mês letivo como chão. Aplicada às estimativas.
                        double peso = pesoMesLetivo(mes);
                        Map<Long, Turma> turmaPorId = turmas.stream()
                                        .collect(Collectors.toMap(Turma::getId, tt -> tt));
                        for (Map.Entry<Long, double[]> e2 : rent.entrySet()) {
                                Turma tt = turmaPorId.get(e2.getKey());
                                double frac = tt != null ? fracaoAtividadeTurma(tt, mes, dados.aulas) : 1.0;
                                double fator = Math.min(peso, frac);
                                if (fator == 1.0)
                                        continue;
                                double[] x = e2.getValue();
                                x[RemuneracaoService.REC_EST] *= fator;
                                x[RemuneracaoService.CUSTO_EST] *= fator;
                                x[RemuneracaoService.SALDO_EST] = x[RemuneracaoService.REC_EST]
                                                - x[RemuneracaoService.CUSTO_EST];
                        }

                        // Agrupar as turmas por professor, com subtotal por professor
                        Map<String, List<Turma>> porProf = new LinkedHashMap<>();
                        for (Turma t : turmas) {
                                double[] x = rent.get(t.getId());
                                if (x == null || java.util.Arrays.stream(x).allMatch(vv -> vv == 0))
                                        continue;
                                String prof = t.getProfessor() != null ? t.getProfessor().getNome() : "Sem professor";
                                porProf.computeIfAbsent(prof, k -> new ArrayList<>()).add(t);
                        }
                        List<LinhaRent> raizes = new ArrayList<>();
                        for (Map.Entry<String, List<Turma>> e : porProf.entrySet()) {
                                List<LinhaRent> filhos = e.getValue().stream()
                                                .map(t -> new LinhaRent(t.getDescricao(), false,
                                                                rent.get(t.getId()), List.of()))
                                                .sorted((a, b) -> Double.compare(b.v()[RemuneracaoService.SALDO_EST],
                                                                a.v()[RemuneracaoService.SALDO_EST]))
                                                .collect(Collectors.toList());
                                double[] soma = new double[6];
                                filhos.forEach(f -> {
                                        for (int i = 0; i < 6; i++)
                                                soma[i] += f.v()[i];
                                });
                                raizes.add(new LinhaRent(e.getKey(), true, soma, filhos));
                        }
                        raizes.sort((a, b) -> Double.compare(b.v()[RemuneracaoService.SALDO_EST],
                                        a.v()[RemuneracaoService.SALDO_EST]));

                        com.vaadin.flow.component.treegrid.TreeGrid<LinhaRent> grid =
                                        new com.vaadin.flow.component.treegrid.TreeGrid<>();
                        grid.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_ROW_STRIPES);
                        grid.setSizeFull();
                        grid.setItems(raizes, LinhaRent::filhos);
                        grid.expandRecursively(raizes, 1);
                        grid.setPartNameGenerator(lr -> lr.grupo() ? "rent-grp" : null);

                        grid.addHierarchyColumn(LinhaRent::nome).setHeader("Professor / Turma")
                                        .setAutoWidth(true).setFlexGrow(1);
                        Grid.Column<LinhaRent> cRecR = colRent(grid, RemuneracaoService.REC_REAL, "Real", false, false, false);
                        Grid.Column<LinhaRent> cRecE = colRent(grid, RemuneracaoService.REC_EST, "Estimada", true, false, true);
                        Grid.Column<LinhaRent> cCusR = colRent(grid, RemuneracaoService.CUSTO_REAL, "Real", false, false, false);
                        Grid.Column<LinhaRent> cCusE = colRent(grid, RemuneracaoService.CUSTO_EST, "Estimado", true, false, true);
                        Grid.Column<LinhaRent> cSalR = colRent(grid, RemuneracaoService.SALDO_REAL, "Real", false, true, false);
                        Grid.Column<LinhaRent> cSalE = colRent(grid, RemuneracaoService.SALDO_EST, "Estimado", true, true, false);

                        com.vaadin.flow.component.grid.HeaderRow topo = grid.prependHeaderRow();
                        topo.join(cRecR, cRecE).setComponent(grupoHeader("Receita", true));
                        topo.join(cCusR, cCusE).setComponent(grupoHeader("Custo Prof.", true));
                        topo.join(cSalR, cSalE).setComponent(grupoHeader("Saldo", false));

                        String[] headers = { "Professor / Turma", "Receita Real", "Receita Estimada", "Custo Prof Real",
                                        "Custo Prof Estimado", "Saldo Real", "Saldo Estimado" };
                        List<String[]> rows = new ArrayList<>();
                        double[] tot = new double[6];
                        for (LinhaRent g : raizes) {
                                rows.add(linhaExport("[" + g.nome() + "]", g.v()));
                                for (LinhaRent f : g.filhos())
                                        rows.add(linhaExport("   " + f.nome(), f.v()));
                                for (int i = 0; i < 6; i++)
                                        tot[i] += g.v()[i];
                        }
                        rows.add(linhaExport("TOTAL", tot));

                        Span aviso = new Span(previsto
                                        ? "Mês futuro: as colunas Real ainda estão praticamente a zero (mensalidades por emitir, horas por registar) — orienta-te pelas colunas Estimadas."
                                        : "Real = faturado / horas registadas. Estimado = projeção das inscrições ativas e horário planeado.");
                        aviso.getStyle().set("font-size", "12px").set("color", previsto ? "#e65100" : "#888");

                        Span legendaCores = new Span();
                        legendaCores.getElement().setProperty("innerHTML",
                                        "<span style='background:#FFF3E0;padding:1px 8px;border-radius:4px'>Real</span>"
                                        + "&nbsp;&nbsp;"
                                        + "<span style='background:#FFFDE7;padding:1px 8px;border-radius:4px'>Estimado</span>");
                        legendaCores.getStyle().set("font-size", "12px");
                        HorizontalLayout linhaAviso = new HorizontalLayout(aviso, legendaCores);
                        linhaAviso.setWidthFull();
                        linhaAviso.setJustifyContentMode(com.vaadin.flow.component.orderedlayout.FlexComponent.JustifyContentMode.BETWEEN);
                        linhaAviso.setAlignItems(com.vaadin.flow.component.orderedlayout.FlexComponent.Alignment.CENTER);

                        String tituloExport = "Rentabilidade - " + mesLabel(mes) + (previsto ? " (previsao)" : "");
                        List<BarraRent> barras = dadosGraficoRentabilidade(mes, turmas, studio, dados);
                        Component grafico = criarGraficoRentabilidadeMensal(barras);

                        Anchor aPdf = new Anchor(gerarPdfRentabilidade(tituloExport, raizes, tot,
                                        renderGraficoRentabilidadePng(barras)), "");
                        aPdf.getElement().setAttribute("download", true);
                        Button bPdf = new Button("Download PDF", VaadinIcon.DOWNLOAD.create());
                        bPdf.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
                        bPdf.getStyle().set("background-color", "#FF8C00");
                        aPdf.add(bPdf);
                        Anchor aXls = new Anchor(gerarExcelGenerico(tituloExport, LARANJA_DANCE, headers, rows), "");
                        aXls.getElement().setAttribute("download", true);
                        Button bXls = new Button("Download Excel", VaadinIcon.DOWNLOAD.create());
                        bXls.addThemeVariants(ButtonVariant.LUMO_PRIMARY);
                        bXls.getStyle().set("background-color", "#FF8C00");
                        aXls.add(bXls);
                        HorizontalLayout downloadsRent = new HorizontalLayout(aPdf, aXls);

                        VerticalLayout v = new VerticalLayout(grafico, linhaAviso, grid, downloadsRent);
                        v.setSizeFull();
                        v.setPadding(false);
                        v.expand(grid);
                        container.add(v);
                };
                seletor.addValueChangeListener(e -> render.run());
                render.run();

                VerticalLayout wrap = new VerticalLayout(seletor, container);
                wrap.setSizeFull();
                wrap.expand(container);
                d.add(wrap);
                d.getFooter().add(new Button("Fechar", e -> d.close()));
                d.open();
        }

        /**
         * Torna a modal redimensionável e arrastável, e acrescenta ao cabeçalho um
         * botão que alterna entre o tamanho normal e quase ecrã inteiro.
         */
        private void dialogRedimensionavel(Dialog d, String larguraDefault, String alturaDefault) {
                d.setWidth(larguraDefault);
                d.setHeight(alturaDefault);
                d.setResizable(true);
                d.setDraggable(true);

                boolean[] maximizado = { false };
                Button btn = new Button(VaadinIcon.EXPAND_FULL.create());
                btn.addThemeVariants(ButtonVariant.LUMO_TERTIARY, ButtonVariant.LUMO_SMALL);
                btn.getElement().setAttribute("aria-label", "Aumentar / reduzir janela");
                btn.getElement().setAttribute("title", "Aumentar / reduzir janela");
                btn.addClickListener(e -> {
                        maximizado[0] = !maximizado[0];
                        if (maximizado[0]) {
                                d.setWidth("96vw");
                                d.setHeight("94vh");
                                btn.setIcon(VaadinIcon.COMPRESS.create());
                        } else {
                                d.setWidth(larguraDefault);
                                d.setHeight(alturaDefault);
                                btn.setIcon(VaadinIcon.EXPAND_FULL.create());
                        }
                });
                d.getHeader().add(btn);
        }

        /** Linha do relatório de rentabilidade: grupo (professor) ou turma. v = [recR, recE, custoR, custoE, saldoR, saldoE]. */
        private record LinhaRent(String nome, boolean grupo, double[] v, List<LinhaRent> filhos) {
        }

        private Grid.Column<LinhaRent> colRent(Grid<LinhaRent> grid, int idx, String sub,
                        boolean estimado, boolean saldo, boolean separador) {
                Grid.Column<LinhaRent> c = grid.addColumn(lr -> fmtEuro(lr.v()[idx]))
                                .setHeader(subHeader(sub, estimado)).setAutoWidth(true)
                                .setTextAlign(ColumnTextAlign.CENTER);
                c.setPartNameGenerator(lr -> {
                        String base = estimado ? "rent-est" : "rent-real";
                        if (saldo)
                                base += lr.v()[idx] >= 0 ? " rent-pos" : " rent-neg";
                        if (separador)
                                base += " rent-sep";
                        return base;
                });
                return c;
        }

        private Span subHeader(String txt, boolean estimado) {
                Span s = new Span(txt);
                s.getStyle().set("width", "100%").set("text-align", "center")
                                .set("font-size", "0.78em").set("font-weight", "600")
                                .set("color", estimado ? "#F9A825" : "#E65100");
                return s;
        }

        private Span grupoHeader(String txt, boolean separador) {
                Span s = new Span(txt);
                s.getStyle().set("display", "block").set("width", "100%").set("text-align", "center")
                                .set("font-weight", "700");
                if (separador)
                        s.getStyle().set("border-right", "2px solid #b0bec5").set("padding-right", "0");
                return s;
        }

        private String[] linhaExport(String nome, double[] x) {
                return new String[] { nome, fmtEuro(x[0]), fmtEuro(x[1]), fmtEuro(x[2]), fmtEuro(x[3]),
                                fmtEuro(x[4]), fmtEuro(x[5]) };
        }

        // Peso do mês no ano letivo — usado como "chão" quando as aulas da turma não
        // têm datas de início/fim no mapa de salas. Julho/Agosto: 0. Setembro: 0,5.
        private double pesoMesLetivo(YearMonth mes) {
                return switch (mes.getMonthValue()) {
                        case 7, 8 -> 0.0;
                        case 9 -> 0.5;
                        default -> 1.0;
                };
        }

        // Fração do mês em que a turma tem aulas a decorrer (dentro do período
        // início/fim de cada aula e fora das interrupções letivas), face a um mês
        // cheio. 1,0 se nada limitar.
        private double fracaoAtividadeTurma(Turma t, YearMonth mes, List<Aula> aulas) {
                double reais = 0, cheio = 0;
                for (Aula a : aulas) {
                        if (a.getTurma() == null || !a.getTurma().getId().equals(t.getId()))
                                continue;
                        if (a.getDia() == null || "ENSAIO".equalsIgnoreCase(a.getTipo()))
                                continue;
                        reais += ocorrenciasNoMes(a.getDia(), mes, a.getDataInicio(), a.getDataFim(), true);
                        cheio += ocorrenciasNoMes(a.getDia(), mes, null, null, false);
                }
                return cheio == 0 ? 1.0 : Math.min(1.0, reais / cheio);
        }

        private long ocorrenciasNoMes(DayOfWeek dia, YearMonth mes, LocalDate ini, LocalDate fim,
                        boolean comInterrupcoes) {
                LocalDate from = mes.atDay(1);
                LocalDate to = mes.atEndOfMonth();
                if (ini != null && ini.isAfter(from))
                        from = ini;
                if (fim != null && fim.isBefore(to))
                        to = fim;
                long c = 0;
                for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
                        if (d.getDayOfWeek() != dia)
                                continue;
                        final LocalDate dia0 = d;
                        if (comInterrupcoes && interrupcoesLetivas.stream().anyMatch(x -> x.contem(dia0)))
                                continue;
                        c++;
                }
                return c;
        }

        // Turma "no mapa de salas": tem pelo menos uma aula com sala, dia e horas
        // definidos. Só estas entram no relatório de rentabilidade.
        private boolean temAulasRegularesPlaneadas(Turma t, List<Aula> aulas) {
                return aulas.stream().anyMatch(a -> a.getTurma() != null
                                && a.getTurma().getId().equals(t.getId())
                                && a.getSala() != null && a.getDia() != null
                                && a.getHoraInicio() != null && a.getHoraFim() != null);
        }

        /** Uma barra do gráfico de rentabilidade mensal. */
        private record BarraRent(String label, double valor, boolean real, boolean selecionado) {
        }

        private List<BarraRent> dadosGraficoRentabilidade(YearMonth mesSel, List<Turma> turmas, Studio studio,
                        RemuneracaoService.Dados dados) {
                List<BarraRent> out = new ArrayList<>();
                YearMonth inicio = mesSel.minusMonths(6);
                for (int i = 0; i < 12; i++) {
                        YearMonth m = inicio.plusMonths(i);
                        boolean futuro = remuneracaoService.ehFuturo(m);
                        double total = remuneracaoService.rentabilidadePorTurma(turmas, studio, m, dados)
                                        .values().stream().mapToDouble(x -> x[2]).sum();
                        if (futuro)
                                total *= pesoMesLetivo(m);
                        out.add(new BarraRent(
                                        m.getMonth().getDisplayName(TextStyle.SHORT, new Locale("pt")).replace(".", "")
                                                        + " " + String.valueOf(m.getYear()).substring(2),
                                        Math.round(total * 100.0) / 100.0, !futuro, m.equals(mesSel)));
                }
                return out;
        }

        private byte[] renderGraficoRentabilidadePng(List<BarraRent> barras) {
                int w = 900, h = 300, padL = 55, padR = 15, padT = 18, padB = 38;
                BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
                Graphics2D g = img.createGraphics();
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setColor(Color.WHITE);
                g.fillRect(0, 0, w, h);
                int plotW = w - padL - padR, plotH = h - padT - padB;
                double max = Math.max(0, barras.stream().mapToDouble(BarraRent::valor).max().orElse(0));
                double min = Math.min(0, barras.stream().mapToDouble(BarraRent::valor).min().orElse(0));
                double range = (max - min) == 0 ? 1 : (max - min);
                int zeroY = padT + (int) Math.round(plotH * (max / range));
                g.setColor(new Color(0xCC, 0xCC, 0xCC));
                g.drawLine(padL, zeroY, w - padR, zeroY);
                int n = Math.max(1, barras.size());
                double slot = plotW / (double) n;
                double bw = Math.min(42, slot * 0.6);
                g.setFont(new java.awt.Font("SansSerif", java.awt.Font.PLAIN, 10));
                for (int i = 0; i < barras.size(); i++) {
                        BarraRent b = barras.get(i);
                        double cx = padL + slot * (i + 0.5);
                        int barH = Math.max(1, (int) Math.round(plotH * (Math.abs(b.valor()) / range)));
                        int y = b.valor() >= 0 ? zeroY - barH : zeroY;
                        g.setColor(b.real() ? new Color(255, 140, 0) : new Color(255, 193, 7));
                        g.fillRoundRect((int) (cx - bw / 2), y, (int) bw, barH, 6, 6);
                        if (b.selecionado()) {
                                g.setColor(new Color(0x2D, 0x34, 0x36));
                                g.setStroke(new BasicStroke(2f));
                                g.drawRoundRect((int) (cx - bw / 2), y, (int) bw, barH, 6, 6);
                        }
                        g.setColor(new Color(0x55, 0x55, 0x55));
                        int lw = g.getFontMetrics().stringWidth(b.label());
                        g.drawString(b.label(), (int) (cx - lw / 2), h - padB + 14);
                        String val = String.format("%.0f", b.valor());
                        int vw = g.getFontMetrics().stringWidth(val);
                        g.drawString(val, (int) (cx - vw / 2), b.valor() >= 0 ? y - 3 : y + barH + 12);
                }
                g.dispose();
                try {
                        ByteArrayOutputStream bos = new ByteArrayOutputStream();
                        ImageIO.write(img, "png", bos);
                        return bos.toByteArray();
                } catch (Exception e) {
                        return null;
                }
        }

        private PdfPCell celPdf(String txt, Color bg, boolean bold, int align, boolean textoBranco) {
                com.lowagie.text.Font f = FontFactory.getFont(
                                bold ? FontFactory.HELVETICA_BOLD : FontFactory.HELVETICA, 9,
                                com.lowagie.text.Font.NORMAL, textoBranco ? Color.WHITE : Color.BLACK);
                PdfPCell c = new PdfPCell(new Phrase(txt, f));
                c.setPadding(4);
                c.setHorizontalAlignment(align);
                c.setVerticalAlignment(Element.ALIGN_MIDDLE);
                if (bg != null)
                        c.setBackgroundColor(bg);
                return c;
        }

        private PdfPCell celPdf(String txt, Color bg, boolean bold, int align) {
                return celPdf(txt, bg, bold, align, false);
        }

        // PDF do relatório de rentabilidade: gráfico + tabela agrupada por professor
        // (mesmo layout da modal), em A4 horizontal.
        private StreamResource gerarPdfRentabilidade(String titulo, List<LinhaRent> raizes, double[] total,
                        byte[] graficoPng) {
                return new StreamResource(titulo.replace(" ", "_") + ".pdf", () -> {
                        try {
                                ByteArrayOutputStream baos = new ByteArrayOutputStream();
                                Document doc = new Document(PageSize.A4.rotate());
                                PdfWriter.getInstance(doc, baos);
                                doc.open();
                                adicionarLogo(doc);

                                Paragraph t = new Paragraph(titulo,
                                                FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16));
                                t.setAlignment(Element.ALIGN_CENTER);
                                t.setSpacingAfter(14);
                                doc.add(t);

                                if (graficoPng != null) {
                                        Image img = Image.getInstance(graficoPng);
                                        img.scaleToFit(770, 250);
                                        img.setAlignment(Element.ALIGN_CENTER);
                                        img.setSpacingAfter(16);
                                        doc.add(img);
                                }

                                Color laranjaClaro = new Color(0xFF, 0xF3, 0xE0);
                                Color amareloClaro = new Color(0xFF, 0xFD, 0xE7);
                                Color cinza = new Color(0xEC, 0xEF, 0xF1);
                                Color[] bgCols = { null, laranjaClaro, amareloClaro, laranjaClaro, amareloClaro,
                                                laranjaClaro, amareloClaro };

                                PdfPTable tab = new PdfPTable(7);
                                tab.setWidthPercentage(100);
                                tab.setWidths(new float[] { 3.4f, 1, 1, 1, 1, 1, 1 });
                                tab.setHeaderRows(2);

                                PdfPCell cProfTurma = celPdf("Professor / Turma", LARANJA_DANCE, true,
                                                Element.ALIGN_LEFT, true);
                                cProfTurma.setRowspan(2);
                                tab.addCell(cProfTurma);
                                for (String gh : new String[] { "Receita", "Custo Prof.", "Saldo" }) {
                                        PdfPCell c = celPdf(gh, LARANJA_DANCE, true, Element.ALIGN_CENTER, true);
                                        c.setColspan(2);
                                        tab.addCell(c);
                                }
                                for (int i = 0; i < 3; i++) {
                                        tab.addCell(celPdf("Real", LARANJA_DANCE, true, Element.ALIGN_CENTER, true));
                                        tab.addCell(celPdf("Est.", LARANJA_DANCE, true, Element.ALIGN_CENTER, true));
                                }

                                for (LinhaRent g : raizes) {
                                        tab.addCell(celPdf(g.nome(), cinza, true, Element.ALIGN_LEFT));
                                        for (int i = 0; i < 6; i++)
                                                tab.addCell(celPdf(fmtEuro(g.v()[i]), cinza, true, Element.ALIGN_CENTER));
                                        for (LinhaRent f : g.filhos()) {
                                                tab.addCell(celPdf("   " + f.nome(), null, false, Element.ALIGN_LEFT));
                                                for (int i = 0; i < 6; i++)
                                                        tab.addCell(celPdf(fmtEuro(f.v()[i]), bgCols[i + 1], false,
                                                                        Element.ALIGN_CENTER));
                                        }
                                }
                                tab.addCell(celPdf("TOTAL", LARANJA_DANCE, true, Element.ALIGN_LEFT, true));
                                for (int i = 0; i < 6; i++)
                                        tab.addCell(celPdf(fmtEuro(total[i]), LARANJA_DANCE, true, Element.ALIGN_CENTER,
                                                        true));
                                doc.add(tab);

                                Paragraph rodape = new Paragraph("\nGerado em: " + LocalDate.now().format(fmt),
                                                FontFactory.getFont(FontFactory.HELVETICA, 8,
                                                                com.lowagie.text.Font.NORMAL, Color.GRAY));
                                rodape.setAlignment(Element.ALIGN_RIGHT);
                                doc.add(rodape);

                                doc.close();
                                return new ByteArrayInputStream(baos.toByteArray());
                        } catch (Exception e) {
                                return null;
                        }
                });
        }

        // Gráfico de barras com a rentabilidade geral (soma de todas as turmas) mês a
        // mês, numa janela de 12 meses à volta do mês selecionado. Meses fechados
        // mostram o valor real; os futuros mostram a estimativa, em cor distinta; o
        // mês selecionado fica realçado com contorno.
        private Component criarGraficoRentabilidadeMensal(List<BarraRent> barras) {
                List<String> labels = new ArrayList<>();
                List<Double> valores = new ArrayList<>();
                List<String> cores = new ArrayList<>();
                List<String> bordas = new ArrayList<>();
                List<Integer> larguraBorda = new ArrayList<>();

                for (BarraRent b : barras) {
                        labels.add(b.label());
                        valores.add(b.valor());
                        cores.add(b.real() ? "rgba(255,140,0,0.85)" : "rgba(255,193,7,0.55)");
                        boolean sel = b.selecionado();
                        bordas.add(sel ? "#2D3436" : "rgba(0,0,0,0)");
                        larguraBorda.add(sel ? 2 : 0);
                }

                Map<String, Object> dataset = new LinkedHashMap<>();
                dataset.put("data", valores);
                dataset.put("backgroundColor", cores);
                dataset.put("borderColor", bordas);
                dataset.put("borderWidth", larguraBorda);
                dataset.put("borderRadius", 6);
                dataset.put("maxBarThickness", 46);

                Map<String, Object> data = new LinkedHashMap<>();
                data.put("labels", labels);
                data.put("datasets", List.of(dataset));

                Map<String, Object> legend = new LinkedHashMap<>();
                legend.put("display", false);
                Map<String, Object> tooltip = new LinkedHashMap<>();
                tooltip.put("backgroundColor", "#2D3436");
                tooltip.put("padding", 10);
                tooltip.put("cornerRadius", 8);
                Map<String, Object> plugins = new LinkedHashMap<>();
                plugins.put("legend", legend);
                plugins.put("tooltip", tooltip);

                Map<String, Object> gridY = new LinkedHashMap<>();
                gridY.put("color", "#f0f0f0");
                Map<String, Object> scaleY = new LinkedHashMap<>();
                scaleY.put("grid", gridY);
                Map<String, Object> gridX = new LinkedHashMap<>();
                gridX.put("display", false);
                Map<String, Object> scaleX = new LinkedHashMap<>();
                scaleX.put("grid", gridX);
                Map<String, Object> scales = new LinkedHashMap<>();
                scales.put("y", scaleY);
                scales.put("x", scaleX);

                Map<String, Object> options = new LinkedHashMap<>();
                options.put("responsive", true);
                options.put("maintainAspectRatio", false);
                options.put("plugins", plugins);
                options.put("scales", scales);

                Map<String, Object> config = new LinkedHashMap<>();
                config.put("type", "bar");
                config.put("data", data);
                config.put("options", options);

                String json;
                try {
                        json = new ObjectMapper().writeValueAsString(config);
                } catch (Exception ex) {
                        json = "{}";
                }

                ChartContainer chart = new ChartContainer() {
                };
                chart.setWidthFull();
                chart.setHeight("210px");
                chart.showChart(json);

                HorizontalLayout legenda = new HorizontalLayout(
                                legendaItem("rgba(255,140,0,0.85)", "Real (meses fechados)"),
                                legendaItem("rgba(255,193,7,0.75)", "Estimativa (meses futuros)"));
                legenda.getStyle().set("gap", "18px").set("flex-wrap", "wrap").set("margin-top", "8px")
                                .set("font-size", "0.8em").set("color", "#555");

                VerticalLayout wrap = new VerticalLayout(chart, legenda);
                wrap.setPadding(false);
                wrap.setSpacing(false);
                wrap.setWidthFull();
                wrap.getStyle().set("border", "1px solid #eee").set("border-radius", "10px").set("padding", "12px");
                return wrap;
        }

        private Span legendaItem(String cor, String texto) {
                Span dot = new Span();
                dot.getStyle().set("display", "inline-block").set("width", "10px").set("height", "10px")
                                .set("border-radius", "3px").set("background", cor).set("margin-right", "6px");
                Span item = new Span(dot, new Span(texto));
                item.getStyle().set("display", "inline-flex").set("align-items", "center");
                return item;
        }

        private void abrirRelatorioSeguros() {
                pt.studioflow.model.Studio _sSeg = pt.studioflow.config.TenantContext.getCurrentStudio();
                List<Aluno> seguros = (_sSeg != null ? alunoRepository.findAllByStudio(_sSeg) : alunoRepository.findAll()).stream()
                                .filter(a -> a.isAtivo() && "Associação".equalsIgnoreCase(a.getSeguroDesportivo()))
                                .collect(Collectors.toList());
                Grid<Aluno> grid = new Grid<>();
                grid.setItems(seguros);
                grid.addColumn(Aluno::getNomeCompleto).setHeader("Nome");
                List<String[]> rows = seguros.stream().map(a -> new String[] { a.getNomeCompleto(),
                                a.getDataNascimento().format(fmt), a.getNumeroContribuinte(),
                                String.valueOf(a.getNumeroSocio()) })
                                .collect(Collectors.toList());
                configurarDialogComGrid("Seguros Associação", grid, rows,
                                new String[] { "Nome", "Data Nasc.", "NIF", "Nº Sócio" });
        }

        private List<Map<String, Object>> obterDadosDevedores() {
                pt.studioflow.model.Studio _sDev = pt.studioflow.config.TenantContext.getCurrentStudio();
                return (_sDev != null ? alunoRepository.findAllByStudio(_sDev) : alunoRepository.findAll()).stream().map(a -> {
                        List<Mensalidade> div = new ArrayList<>();
                        div.addAll(mensalidadeRepository.findByAlunoAndEstado(a, EstadoMensalidade.FATURADO));
                        div.addAll(mensalidadeRepository.findByAlunoAndEstado(a, EstadoMensalidade.EM_DIVIDA));
                        if (div.isEmpty())
                                return null;
                        Map<String, Object> map = new HashMap<>();
                        map.put("nome", a.getNomeCompleto());
                        map.put("telemovel", a.getTelemovel() != null ? a.getTelemovel() : "-");
                        map.put("email", a.getEmail() != null ? a.getEmail() : "-");
                        map.put("total", div.stream().mapToDouble(Mensalidade::getValor).sum());
                        return map;
                }).filter(Objects::nonNull)
                                .sorted(Comparator.comparingDouble((Map<String, Object> m) -> (Double) m.get("total"))
                                                .reversed())
                                .collect(Collectors.toList());
        }

        private String formatarNome(String nome) {
                if (nome == null || nome.isBlank()) return "";
                String[] partes = nome.trim().split("\\s+");
                return partes.length <= 1 ? partes[0] : partes[0] + " " + partes[partes.length - 1];
        }

        private String calcularIdade(java.time.LocalDate nascimento) {
                if (nascimento == null) return "-";
                return String.valueOf(java.time.Period.between(nascimento, java.time.LocalDate.now()).getYears());
        }
}
