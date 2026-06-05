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
import com.vaadin.flow.component.button.Button;
import com.vaadin.flow.component.button.ButtonVariant;
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
import pt.studioflow.model.*;
import pt.studioflow.repository.*;
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

        private final Color LARANJA_DANCE = new Color(255, 140, 0);
        private final DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd/MM/yyyy");

        public RelatoriosView(AlunoRepository alunoRepository, TurmaRepository turmaRepository,
                        AlunoTurmaRepository alunoTurmaRepository, MensalidadeRepository mensalidadeRepository,
                        RegistoHorasRepository registoHorasRepository, ProfessorRepository professorRepository) {
                this.alunoRepository = alunoRepository;
                this.turmaRepository = turmaRepository;
                this.alunoTurmaRepository = alunoTurmaRepository;
                this.mensalidadeRepository = mensalidadeRepository;
                this.registoHorasRepository = registoHorasRepository;
                this.professorRepository = professorRepository;

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

        // --- 1. RELATÓRIO PROFESSORES (COM EMAIL) ---
        private void abrirRelatorioProfessores() {
                YearMonth mesAlvo = YearMonth.now().minusMonths(1);
                String nomeMes = mesAlvo.getMonth().getDisplayName(TextStyle.FULL, new Locale("pt"));
                List<Map<String, Object>> dados = obterDadosPagamentos(mesAlvo);

                Grid<Map<String, Object>> grid = new Grid<>();
                grid.setItems(dados);
                grid.addColumn(m -> m.get("prof")).setHeader("Professor");
                grid.addColumn(m -> String.format("%.2f €", m.get("total"))).setHeader("Total").getStyle().set(
                                "font-weight",
                                "bold");

                grid.addComponentColumn(m -> {
                        // 1. Criar o botão
                        Button btnEmail = new Button(VaadinIcon.ENVELOPE.create());
                        btnEmail.addThemeVariants(ButtonVariant.LUMO_PRIMARY, ButtonVariant.LUMO_SMALL);

                        // 2. Configurar a ação do clique
                        btnEmail.addClickListener(e -> {
                                // Obter o nome (String) que está no mapa
                                String nomeProfessor = m.get("prof").toString();

                                // Buscar o e-mail na base de dados pelo nome
                                // Nota: Certifica-te que o professorRepository está disponível na View
                                String emailProf = professorRepository.findByNome(nomeProfessor)
                                                .map(Professor::getEmail)
                                                .orElse(""); // Se não encontrar, fica vazio

                                String pNome = nomeProfessor.split(" ")[0];
                                String valor = String.format("%.2f", (Double) m.get("total"));
                                String assunto = "Pagamento de Aulas - " + nomeMes;

                                String corpo = String.format(
                                                "Olá %s,\n\nA fim de podermos efetuar a transferência bancária referente às aulas do mês de %s, solicitamos o envio do recibo no valor de %s€.\n\nObrigada,\n\n---\n*** Mensagem enviada a partir do Plataforma CoreoFlow ***",
                                                pNome, nomeMes, valor);

                                String mailto = "mailto:" + emailProf + "?subject="
                                                + URLEncoder.encode(assunto, StandardCharsets.UTF_8).replace("+", "%20")
                                                + "&body="
                                                + URLEncoder.encode(corpo, StandardCharsets.UTF_8).replace("+", "%20");

                                getUI().ifPresent(ui -> ui.getPage().open(mailto, "_self"));
                        });

                        // 3. IMPORTANTE: Retornar o componente para a Grid
                        return btnEmail;
                }).setHeader("Enviar E-mail");

                List<String[]> rows = dados.stream()
                                .map(m -> new String[] { m.get("prof").toString(),
                                                String.format("%.2f €", m.get("base")),
                                                String.format("%.2f €", m.get("bonus")),
                                                String.format("%.2f €", m.get("total")) })
                                .collect(Collectors.toList());
                configurarDialogComGrid("Pagamentos Profs - " + nomeMes, grid, rows,
                                new String[] { "Professor", "Horas/Ens.", "Bónus", "Total" });
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
                grid.addColumn(m -> m.get("nome")).setHeader("Aluno");
                grid.addColumn(m -> String.format("%.2f €", m.get("total"))).setHeader("Total");
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
                        b.addThemeVariants(ButtonVariant.LUMO_SUCCESS, ButtonVariant.LUMO_SMALL);
                        return b;
                }).setHeader("WhatsApp");
                List<String[]> rows = dados.stream()
                                .map(m -> new String[] { m.get("nome").toString(), m.get("telemovel").toString(),
                                                m.get("email").toString(), String.format("%.2f €", m.get("total")) })
                                .collect(Collectors.toList());
                configurarDialogComGrid("Gestão de Dívidas", grid, rows,
                                new String[] { "Aluno", "Telemóvel", "Email", "Total" });
        }

        private void abrirRelatorioRentabilidade() {
                YearMonth mes = YearMonth.now();
                pt.studioflow.model.Studio _sRent = pt.studioflow.config.TenantContext.getCurrentStudio();
                java.util.List<Mensalidade> _mensRent = _sRent != null ? mensalidadeRepository.findAllByStudio(_sRent) : mensalidadeRepository.findAll();
                List<Map<String, Object>> dados = (_sRent != null ? turmaRepository.findAllByStudio(_sRent) : turmaRepository.findAll()).stream().map(t -> {
                        double rec = _mensRent.stream()
                                        .filter(m -> m.getTurma() != null && m.getTurma().getId().equals(t.getId())
                                                        && m.getMes().getValue() == mes.getMonthValue()
                                                        && m.getAno() == mes.getYear())
                                        .mapToDouble(Mensalidade::getValor).sum();
                        double custo = (_sRent != null ? registoHorasRepository.findAllByStudio(_sRent) : registoHorasRepository.findAll()).stream().filter(
                                        r -> r.getProfessor().equalsIgnoreCase(
                                                        t.getProfessor() != null ? t.getProfessor().getNome() : "")
                                                        && r.getAno() == mes.getYear()
                                                        && r.getMesNumero() == mes.getMonthValue()
                                                        && !r.getTipoAtividade().equalsIgnoreCase("ENSAIO"))
                                        .mapToDouble(r -> (Duration.between(r.getInicio(), r.getFim()).toMinutes()
                                                        / 60.0)
                                                        * (r.getProfessor().equalsIgnoreCase("Helena Cação") ? 15.0
                                                                        : r.getProfessor().equalsIgnoreCase(
                                                                                        "Leonor Ribeiro") ? 20.0
                                                                                                        : 25.0))
                                        .sum();
                        Map<String, Object> map = new HashMap<>();
                        map.put("turma", t.getDescricao());
                        map.put("rec", rec);
                        map.put("custo", custo);
                        map.put("saldo", rec - custo);
                        return map;
                }).sorted((m1, m2) -> Double.compare((double) m2.get("saldo"), (double) m1.get("saldo")))
                                .collect(Collectors.toList());
                Grid<Map<String, Object>> grid = new Grid<>();
                grid.setItems(dados);
                grid.addColumn(m -> m.get("turma")).setHeader("Turma");
                grid.addColumn(m -> String.format("%.2f €", m.get("saldo"))).setHeader("Saldo");
                List<String[]> rows = dados.stream()
                                .map(m -> new String[] { m.get("turma").toString(),
                                                String.format("%.2f €", m.get("rec")),
                                                String.format("%.2f €", m.get("custo")),
                                                String.format("%.2f €", m.get("saldo")) })
                                .collect(Collectors.toList());
                configurarDialogComGrid("Rentabilidade", grid, rows,
                                new String[] { "Turma", "Receita", "Custo Prof", "Saldo Final" });
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

        private List<Map<String, Object>> obterDadosPagamentos(YearMonth mes) {
                Map<String, Double> pag = new HashMap<>();
                Map<String, Integer> alu = new HashMap<>();
                pt.studioflow.model.Studio _sPagReg = pt.studioflow.config.TenantContext.getCurrentStudio();
                (_sPagReg != null ? registoHorasRepository.findAllByStudio(_sPagReg) : registoHorasRepository.findAll()).stream()
                                .filter(r -> r.getAno() == mes.getYear() && r.getMesNumero() == mes.getMonthValue())
                                .forEach(r -> {
                                        double h = (Duration.between(r.getInicio(), r.getFim()).toMinutes() / 60.0);
                                        double vh = r.getTipoAtividade().equalsIgnoreCase("ENSAIO") ? 10.0 : 25.0;
                                        if (!r.getTipoAtividade().equalsIgnoreCase("ENSAIO")) {
                                                if (r.getProfessor().equalsIgnoreCase("Helena Cação"))
                                                        vh = 15.0;
                                                else if (r.getProfessor().equalsIgnoreCase("Leonor Ribeiro"))
                                                        vh = 20.0;
                                        }
                                        pag.put(r.getProfessor(), pag.getOrDefau