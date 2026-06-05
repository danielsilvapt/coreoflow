package pt.studioflow.service;

import java.awt.Color;
import java.io.OutputStream;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.TextStyle;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.springframework.stereotype.Service;

import com.lowagie.text.Document;
import com.lowagie.text.DocumentException;
import com.lowagie.text.Element;
import com.lowagie.text.Font;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Image;
import com.lowagie.text.ListItem;
import com.lowagie.text.PageSize;
import com.lowagie.text.Paragraph;
import com.lowagie.text.Phrase;
import com.lowagie.text.pdf.PdfPCell;
import com.lowagie.text.pdf.PdfPTable;
import com.lowagie.text.pdf.PdfWriter;

import pt.studioflow.model.Aluno;
import pt.studioflow.model.Convite;
import pt.studioflow.model.Turma;

@Service
public class PdfService {

    public void escreverFichaEvento(Convite convite,
            List<Turma> turmas, // Este é o nome correto do parâmetro
            Map<Long, Integer> contagem, // Este é o nome correto do parâmetro
            List<Aluno> alunosConfirmados,
            OutputStream out) {
        Document document = new Document();
        try {
            PdfWriter.getInstance(document, out);
            document.open();

            // 1. Logotipo
            try {
                Image img = Image.getInstance(getClass().getResource("/static/images/logo-studio.png"));
                img.scaleToFit(120, 120);
                img.setAlignment(Element.ALIGN_CENTER);
                document.add(img);
            } catch (Exception e) {
                Paragraph brand = new Paragraph("ÓBIDOS DANCE", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 20));
                brand.setAlignment(Element.ALIGN_CENTER);
                document.add(brand);
            }

            // 2. Título e Info Geral
            Font fTitulo = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18);
            Paragraph p = new Paragraph("Ficha de Atuação: " + convite.getEvento(), fTitulo);
            p.setAlignment(Element.ALIGN_CENTER);
            p.setSpacingBefore(10);
            document.add(p);

            document.add(new Paragraph("\nData: " + convite.getData() + " às " + convite.getHora()));
            document.add(new Paragraph("Local: " + (convite.getLocal() != null ? convite.getLocal() : "N/A")));
            document.add(new Paragraph("Notas: " + (convite.getObservacoes() != null ? convite.getObservacoes() : "")));

            // 3. Secção de Turmas (Resumo)
            document.add(new Paragraph("\nTurmas Participantes:", FontFactory.getFont(FontFactory.HELVETICA_BOLD)));

            // CORREÇÃO: Usar a variável 'turmas' e 'contagem' que vêm no parâmetro
            for (Turma t : turmas) {
                int totalAlunos = contagem.getOrDefault(t.getId(), 0);
                document.add(
                        new Paragraph("- " + t.getDescricao() + " (" + totalAlunos + " alunos inscritos na turma)"));
            }

            // 4. Secção Detalhada: Alunos que Confirmaram Individualmente
            document.add(
                    new Paragraph("\nLista de Alunos Confirmados:", FontFactory.getFont(FontFactory.HELVETICA_BOLD)));

            if (alunosConfirmados == null || alunosConfirmados.isEmpty()) {
                document.add(new Paragraph("Nenhum aluno confirmou presença individualmente até ao momento.",
                        FontFactory.getFont(FontFactory.HELVETICA, 10, Font.ITALIC)));
            } else {
                // Criar uma lista numerada ou bullets para os nomes
                com.lowagie.text.List list = new com.lowagie.text.List(true, 20); // true = numerada
                for (Aluno aluno : alunosConfirmados) {
                    list.add(new ListItem(aluno.getNomeCompleto(), FontFactory.getFont(FontFactory.HELVETICA, 11)));
                }
                document.add(list);

                document.add(new Paragraph("\nTotal de presenças confirmadas: " + alunosConfirmados.size(),
                        FontFactory.getFont(FontFactory.HELVETICA_BOLD, 11)));
            }

            document.close();
        } catch (DocumentException ex) {
            ex.printStackTrace();
        }
    }

    public void gerarPdfPresencas(Turma turma, YearMonth mes,
            List<pt.studioflow.view.PresencasView.AlunoPresenca> listaAlunos, OutputStream out) {
        // Usamos A4 em modo Horizontal (rotate) para caberem todos os dias do mês
        Document document = new Document(PageSize.A4.rotate());
        try {
            PdfWriter.getInstance(document, out);
            document.open();

            // 1. Logótipo (reaproveitando a tua lógica existente)
            try {
                Image img = Image.getInstance(getClass().getResource("/static/images/logo-studio.png"));
                img.scaleToFit(100, 100);
                img.setAlignment(Element.ALIGN_CENTER);
                document.add(img);
            } catch (Exception e) {
                Paragraph brand = new Paragraph("ÓBIDOS DANCE", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16));
                brand.setAlignment(Element.ALIGN_CENTER);
                document.add(brand);
            }

            // 2. Cabeçalho
            Font fTitulo = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 16);
            Paragraph p = new Paragraph("Mapa de Presenças: " + turma.getDescricao(), fTitulo);
            p.setAlignment(Element.ALIGN_CENTER);
            document.add(p);

            String mesFormatado = mes.getMonth().getDisplayName(TextStyle.FULL, new Locale("pt", "PT")) + " "
                    + mes.getYear();
            Paragraph sub = new Paragraph("Mês de " + mesFormatado, FontFactory.getFont(FontFactory.HELVETICA, 12));
            sub.setAlignment(Element.ALIGN_CENTER);
            sub.setSpacingAfter(15);
            document.add(sub);

            // 3. Tabela de Presenças
            int diasNoMes = mes.lengthOfMonth();
            // Colunas: Aluno + número de dias do mês
            PdfPTable table = new PdfPTable(diasNoMes + 1);
            table.setWidthPercentage(100);

            // Ajuste de larguras: Coluna do nome mais larga (ex: 15%), o resto igual
            float[] widths = new float[diasNoMes + 1];
            widths[0] = 5f; // Peso para o nome
            for (int i = 1; i <= diasNoMes; i++)
                widths[i] = 1f; // Peso para os dias
            table.setWidths(widths);

            // Cabeçalho da Tabela (Números dos dias)
            Font fHead = FontFactory.getFont(FontFactory.HELVETICA_BOLD, 8);
            table.addCell(new Phrase("Aluno", fHead));
            for (int i = 1; i <= diasNoMes; i++) {
                PdfPCell cell = new PdfPCell(new Phrase(String.valueOf(i), fHead));
                cell.setHorizontalAlignment(Element.ALIGN_CENTER);
                table.addCell(cell);
            }

            // 4. Linhas dos Alunos
            Font fCorpo = FontFactory.getFont(FontFactory.HELVETICA, 8);
            for (pt.studioflow.view.PresencasView.AlunoPresenca ap : listaAlunos) {
                // Nome do Aluno
                table.addCell(new Phrase(ap.getAluno().getNomeCompleto(), fCorpo));

                // Presenças (X ou vazio)
                for (int dia = 1; dia <= diasNoMes; dia++) {
                    LocalDate data = mes.atDay(dia);
                    boolean presente = ap.getPresencas().getOrDefault(data, false);

                    PdfPCell cell = new PdfPCell(new Phrase(presente ? "X" : "", fCorpo));
                    cell.setHorizontalAlignment(Element.ALIGN_CENTER);

                    // Highlight dos dias de aula (Verde clarinho)
                    boolean temAula = turma.getAulas().stream().anyMatch(a -> a.getDia() == data.getDayOfWeek());
                    if (temAula) {
                        cell.setBackgroundColor(new Color(230, 255, 230));
                    }

                    table.addCell(cell);
                }
            }

            document.add(table);
            document.close();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}