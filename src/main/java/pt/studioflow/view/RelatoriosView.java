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
import com.vaadin.flow.component.ClickEvent;
import com.vaadin.flow.component.ComponentEventListener;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
import com.vaadin.flow.component.combobox.ComboBox;
import com.vaadin.flow.component.dialog.Dialog;
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

        private final Color LARANJA_DANCE = new Color(255, 140, 0);
        private final DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy");

        public RelatoriosView(AlunoRepository alunoRepository, TurmaRepository turmaRepository,
                        AlunoTurmaRepository alunoTurmaRepository, MensalidadeRepository mensalidadeRepository,
                        RegistoHorasRepository registoHorasRepository, ProfessorRepository professorRepository,
                        AulaRepository aulaRepository, RemuneracaoService remuneracaoService) {
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
                                String msg = "Olá " + pNome
                                                + "! Notamos que a mensalidade da CoreoFlow está pendente ("
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
                List<Turma> turmas = studio != null ? turmaRepository.findAllByStudio(studio)
                                : turmaRepository.findAll();
                RemuneracaoService.Dados dados = carregarDadosRemuneracao(studio, turmas);

                Dialog d = new Dialog();
                d.setHeaderTitle("Rentabilidade Mensal");
                d.setWidth("950px");
                d.setHeight("650px");

                ComboBox<YearMonth> seletor = criarSeletorMes(YearMonth.now().minusMonths(1));
                Div container = new Div();
                container.setWidthFull();
                container.getStyle().set("flex-grow", "1").set("overflow", "auto");

                Runnable render = () -> {
                        container.removeAll();
                        YearMonth mes = seletor.getValue();
                        boolean previsto = remuneracaoService.ehFuturo(mes);
                        Map<Long, double[]> rent = remuneracaoService.rentabilidadePorTurma(turmas, studio, mes, dados);

                        List<Turma> ordenadas = turmas.stream()
                                        .filter(t -> {
                                                double[] x = rent.get(t.getId());
                                                return x != null && (x[0] != 0 || x[1] != 0);
                                        })
                                        .sorted((a, b) -> Double.compare(rent.get(b.getId())[2], rent.get(a.getId())[2]))
                                        .collect(Collectors.toList());

                        Grid<Turma> grid = new Grid<>();
                        grid.setItems(ordenadas);
                        grid.addColumn(Turma::getDescricao).setHeader("Turma");
                        grid.addColumn(t -> fmtEuro(rent.get(t.getId())[0])).setHeader("Receita").setAutoWidth(true);
                        grid.addColumn(t -> fmtEuro(rent.get(t.getId())[1])).setHeader("Custo Prof").setAutoWidth(true);
                        grid.addColumn(t -> fmtEuro(rent.get(t.getId())[2])).setHeader("Saldo").setAutoWidth(true);
                        grid.addThemeVariants(GridVariant.LUMO_COMPACT, GridVariant.LUMO_ROW_STRIPES);
                        grid.setSizeFull();

                        String[] headers = { "Turma", "Receita", "Custo Prof", "Saldo Final" };
                        List<String[]> rows = ordenadas.stream()
                                        .map(t -> new String[] { t.getDescricao(),
                                                        fmtEuro(rent.get(t.getId())[0]), fmtEuro(rent.get(t.getId())[1]),
                                                        fmtEuro(rent.get(t.getId())[2]) })
                                        .collect(Collectors.toList());
                        double tRec = ordenadas.stream().mapToDouble(t -> rent.get(t.getId())[0]).sum();
                        double tCusto = ordenadas.stream().mapToDouble(t -> rent.get(t.getId())[1]).sum();
                        rows.add(new String[] { "TOTAL", fmtEuro(tRec), fmtEuro(tCusto), fmtEuro(tRec - tCusto) });

                        Span aviso = new Span(previsto
                                        ? "⚠️ Mês futuro — previsão: receita das inscrições ativas menos custo estimado do professor."
                                        : "Valores reais do mês selecionado.");
                        aviso.getStyle().set("font-size", "12px").set("color", previsto ? "#e65100" : "#888");

                        String tituloExport = "Rentabilidade - " + mesLabel(mes) + (previsto ? " (previsao)" : "");
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
                        List<Mensalidade> div = mensalidadeRepository
                                        .findByAlunoAndEstado(a, EstadoMensalidade.FATURADO).stream()
                                        .filter(m -> LocalDate.now().isAfter(LocalDate.of(m.getAno(), m.getMes(), 10)))
                                        .collect(Collectors.toList());
                        if (div.isEmpty())
                                return null;
                        Map<String, Object> map = new HashMap<>();
                        map.put("nome", a.getNomeCompleto());
                        map.put("telemovel", a.getTelemovel() != null ? a.getTelemovel() : "-");
                        map.put("email", a.getEmail() != null ? a.getEmail() : "-");
                        map.put("total", div.stream().mapToDouble(Mensalidade::getValor).sum());
                        return map;
                }).filter(Objects::nonNull).collect(Collectors.toList());
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
