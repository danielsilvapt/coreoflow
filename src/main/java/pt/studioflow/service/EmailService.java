package pt.studioflow.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import pt.studioflow.config.AsyncEmailConfig;
import pt.studioflow.config.TenantContext;
import pt.studioflow.model.Aluno;
import pt.studioflow.model.Convite;
import pt.studioflow.model.Professor;
import pt.studioflow.model.Studio;
import pt.studioflow.model.Turma;

import java.util.ArrayList;
import java.util.List;

@Service
public class EmailService {

    // Remetente dos emails. Separado de spring.mail.username porque com relays SMTP
    // (Brevo, etc.) o utilizador de autenticação (ex: b823a0001@smtp-brevo.com) não
    // serve como "From" — este tem de ser um endereço do domínio verificado. Default
    // para spring.mail.username para não partir configs antigas.
    @org.springframework.beans.factory.annotation.Value("${app.mail.from:${spring.mail.username}}")
    private String mailFrom;

    @org.springframework.beans.factory.annotation.Value("${app.base-url:https://app.coreoflow.me}")
    private String baseUrl;

        @Autowired
        private JavaMailSender mailSender;

        @Autowired
        private EmailAssinatura assinatura;

        @Autowired
        private EmailGate emailGate;

        /**
         * Verdadeiro se o SUPERADMIN desativou o envio de emails para este estúdio.
         * Quando verdadeiro, o método de envio deve registar e sair sem enviar.
         */
        private boolean envioBloqueado(Studio studio) {
                if (emailGate.bloqueado(studio)) {
                        System.out.println("Emails desativados para o estúdio "
                                        + (studio != null ? studio.getNome() : "?") + " — envio ignorado.");
                        return true;
                }
                return false;
        }

        /** Estúdio do aluno, tolerando nulo. */
        private Studio studioDe(Aluno aluno) {
                return aluno != null ? aluno.getStudio() : null;
        }

        /** Estúdio ativo na sessão Vaadin (nulo em threads @Async ou contexto SUPERADMIN). */
        private Studio studioDaSessao() {
                try {
                        return TenantContext.getCurrentStudio();
                } catch (RuntimeException e) {
                        return null;
                }
        }

        /**
         * Caixa que recebe as notificações internas de um estúdio (novas inscrições,
         * renovações, pedidos de sala, etc.). Usa o email de contacto do estúdio e,
         * na ausência dele, cai para o remetente da plataforma para não perder o aviso.
         */
        private String emailAdmin(Studio studio) {
                if (studio != null && studio.getEmailContacto() != null
                                && !studio.getEmailContacto().isBlank()) {
                        return studio.getEmailContacto();
                }
                return mailFrom;
        }

        /**
         * Envia uma notificação interna para o estúdio (nova inscrição, renovação,
         * aluno experimental, …) em HTML e já com a assinatura comum da plataforma
         * (logo do estúdio + "powered by CoreoFlow"). Falha em silêncio para não
         * quebrar a ação do utilizador final se o SMTP estiver em baixo.
         */
        private void enviarNotificacaoInterna(Studio studio, String assunto, String corpoHtml) {
                try {
                        MimeMessage mimeMessage = mailSender.createMimeMessage();
                        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
                        helper.setFrom(mailFrom, "CoreoFlow");
                        helper.setTo(emailAdmin(studio));
                        helper.setSubject(assunto);
                        helper.setText(corpoHtml + assinatura.html(studio), true);
                        mailSender.send(mimeMessage);
                } catch (Exception ex) {
                        System.err.println("Falha ao enviar notificação interna \"" + assunto + "\": " + ex.getMessage());
                }
        }

        /**
         * Envia um email HTML a um destinatário externo (aluno, encarregado, professor)
         * já com a assinatura comum da plataforma (logo do estúdio + "powered by CoreoFlow").
         * Reply-To vai para o email de contacto do estúdio.
         */
        private void enviarHtmlComAssinatura(String para, Studio studio, String remetenteNome,
                        String assunto, String corpoHtml) throws Exception {
                MimeMessage mimeMessage = mailSender.createMimeMessage();
                MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
                helper.setFrom(mailFrom, remetenteNome != null && !remetenteNome.isBlank() ? remetenteNome : "CoreoFlow");
                helper.setTo(para);
                String replyTo = emailAdmin(studio);
                if (replyTo != null && !replyTo.isBlank()) helper.setReplyTo(replyTo);
                helper.setSubject(assunto);
                helper.setText(corpoHtml + assinatura.html(studio), true);
                mailSender.send(mimeMessage);
        }

        public void enviarEmailParaLista(Studio studio, Professor professorCorrespondente, List<String> destinatarios,
                        String assunto, String corpoMensagem)
                        throws Exception {
                if (envioBloqueado(studio)) return;
                MimeMessage mimeMessage = mailSender.createMimeMessage();
                // Usamos o Helper para permitir HTML (assinatura com negrito, links, etc)
                MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

                helper.setFrom(mailFrom, "CoreoFlow");

                // Todos os destinatários em Bcc: alunos entre si (privacidade/RGPD) e
                // também o estúdio e o professor da modalidade, para não expor os seus
                // endereços na lista de cópia visível aos alunos.
                List<String> emailsBcc = new ArrayList<>(destinatarios);
                emailsBcc.add(emailAdmin(studio));
                if (professorCorrespondente != null && professorCorrespondente.getEmail() != null
                                && !professorCorrespondente.getEmail().isBlank()) {
                        emailsBcc.add(professorCorrespondente.getEmail());
                }
                helper.setBcc(emailsBcc.toArray(new String[0]));

                helper.setReplyTo(emailAdmin(studio));
                helper.setSubject(assunto);

                // Junta a mensagem escrita no portal (mantendo as quebras de linha) com a
                // assinatura comum da plataforma.
                String conteudoCompleto = corpoMensagem.replace("\n", "<br>") + assinatura.html(studio);
                helper.setText(conteudoCompleto, true); // O 'true' indica que é HTML

                mailSender.send(mimeMessage);
        }

        @Async(AsyncEmailConfig.EMAIL_EXECUTOR)
        public void enviarEmailAprovacaoSala(Studio studio, String destinatario, String nomeProfessor, String sala,
                        String data, String horaInicio, String horaFim) {
                if (envioBloqueado(studio)) return;
                try {
                        MimeMessage mimeMessage = mailSender.createMimeMessage();
                        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

                        helper.setFrom(mailFrom, "CoreoFlow");
                        helper.setTo(destinatario);
                        helper.setSubject("Reserva de Sala Confirmada - CoreoFlow");

                        String corpoMensagem = String.format(
                                        "<div style='font-family: Arial, sans-serif; color: #333;'>" +
                                                        "<h2>Olá, %s!</h2>" +
                                                        "<p>Temos o prazer de informar que o teu pedido de reserva de sala foi <b>APROVADO</b>.</p>"
                                                        +
                                                        "<ul style='background-color: #f0f9f0; padding: 15px; border-left: 4px solid #27ae60; list-style-type: none;'>"
                                                        +
                                                        "  <li><b>Sala:</b> %s</li>" +
                                                        "  <li><b>Data:</b> %s</li>" +
                                                        "  <li><b>Horário Início:</b> %s</li>" +
                                                        "  <li><b>Horário Fim:</b> %s</li>" +
                                                        "</ul>" +
                                                        "<p>Bom ensaio / aula!</p>" +
                                                        "</div>",
                                        nomeProfessor, sala, data, horaInicio, horaFim);

                        helper.setText(corpoMensagem + assinatura.html(studio), true);

                        mailSender.send(mimeMessage);

                        System.out.println(
                                        "Email de aprovação enviado para " + destinatario + " - " + nomeProfessor
                                                        + " - " + sala + " - " + data
                                                        + " " + horaInicio + "-" + horaFim);

                } catch (Exception e) {
                        System.err.println("Erro ao enviar e-mail de aprovação: " + e.getMessage());
                }
        }

        @Async(AsyncEmailConfig.EMAIL_EXECUTOR)
        public void notificarAdminNovoPedido(Studio studio, String nomeProfessor, String tipo, String turma, String sala,
                        String data, String horaInicio, String horaFim, String observacoes) {
                if (envioBloqueado(studio)) return;
                try {
                        MimeMessage mimeMessage = mailSender.createMimeMessage();
                        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

                        helper.setTo(emailAdmin(studio));
                        helper.setFrom(mailFrom, "CoreoFlow");
                        helper.setSubject("Novo Pedido de Reserva de Sala - Pendente de Aprovação");

                        String corpoMensagem = String.format(
                                        "<div style='font-family: Arial, sans-serif; color: #333;'>" +
                                                        "<h2>Novo Pedido de Reserva!</h2>" +
                                                        "<p>O professor <b>%s</b> submeteu um novo pedido que aguarda a tua aprovação:</p>"
                                                        +

                                                        // Barra lateral laranja (#f39c12) e fundo suave (#fef9f2)
                                                        "<ul style='background-color: #fef9f2; padding: 15px; border-left: 4px solid #f39c12; list-style-type: none;'>"
                                                        +
                                                        "  <li><b>Tipo:</b> %s</li>" +
                                                        "  <li><b>Turma:</b> %s</li>" +
                                                        "  <li><b>Sala:</b> %s</li>" +
                                                        "  <li><b>Data:</b> %s</li>" +
                                                        "  <li><b>Horário Início:</b> %s</li>" +
                                                        "  <li><b>Horário Fim:</b> %s</li>" +
                                                        "</ul>" +

                                                        "<p style='margin-top: 20px;'>" +
                                                        "  <a href='" + baseUrl + "/horario-salas' " +
                                                        "  style='background-color: #000; color: #fff; padding: 12px 25px; text-decoration: none; border-radius: 5px; display: inline-block;'>"
                                                        +
                                                        "  Abrir Aplicação para Aprovar</a>" +
                                                        "</p>" +
                                                        "</div>",
                                        nomeProfessor, tipo, turma, sala, data, horaInicio, horaFim, observacoes);

                        helper.setText(corpoMensagem + assinatura.html(studio), true);

                        mailSender.send(mimeMessage);

                        System.out.println("Notificação de novo pedido enviada para o admin: " + nomeProfessor + " - "
                                        + sala);
                } catch (Exception e) {
                        System.err.println("Erro ao enviar notificação de admin: " + e.getMessage());
                }
        }

        @Async(AsyncEmailConfig.EMAIL_EXECUTOR)
        public void enviarEmailRecusaSala(Studio studio, String destinatario, String nomeProfessor, String sala,
                        String data, String horaInicio, String horaFim) {
                if (envioBloqueado(studio)) return;
                try {
                        MimeMessage mimeMessage = mailSender.createMimeMessage();
                        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

                        helper.setFrom(mailFrom, "CoreoFlow");
                        helper.setTo(destinatario);
                        helper.setSubject("Atualização sobre o seu pedido de reserva - CoreoFlow");

                        String corpoMensagem = String.format(
                                        "<div style='font-family: Arial, sans-serif; color: #333;'>" +
                                                        "<h2>Olá, %s.</h2>" +
                                                        "<p>Informamos que, infelizmente, não foi possível aprovar o teu pedido de reserva de sala:</p>"
                                                        +
                                                        "<ul style='background-color: #f9f9f9; padding: 15px; border-left: 4px solid #e74c3c; list-style-type: none;'>"
                                                        +
                                                        "  <li><b>Sala:</b> %s</li>" +
                                                        "  <li><b>Data:</b> %s</li>" +
                                                        "  <li><b>Horário Início:</b> %s</li>" +
                                                        "  <li><b>Horário Fim:</b> %s</li>" +
                                                        "</ul>" +
                                                        "<p>Poderá tratar-se de uma sobreposição de horários ou indisponibilidade do espaço. "
                                                        +
                                                        "Por favor, verifica outros horários disponíveis na aplicação ou contacta a secretaria.</p>"
                                                        +
                                                        "</div>",
                                        nomeProfessor, sala, data, horaInicio, horaFim);

                        helper.setText(corpoMensagem + assinatura.html(studio), true);

                        mailSender.send(mimeMessage);

                        System.out.println(
                                        "Email de recusa enviado para " + destinatario + " - " + nomeProfessor + " - "
                                                        + sala + " - " + data
                                                        + " " + horaInicio + "-" + horaFim);
                } catch (Exception e) {
                        System.err.println("Erro ao enviar e-mail de recusa: " + e.getMessage());
                }
        }

        public void enviarConvocatoria(Aluno aluno, Convite convite) {
                if (envioBloqueado(studioDe(aluno))) return;
                String urlBase = baseUrl + "/api/convocatoria";
                String linkConfirmar = urlBase + "/confirmar?alunoId=" + aluno.getId() + "&conviteId="
                                + convite.getId();
                String linkRecusar = urlBase + "/recusar?alunoId=" + aluno.getId() + "&conviteId=" + convite.getId();

                try {
                        MimeMessage message = mailSender.createMimeMessage();
                        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

                        helper.setFrom(mailFrom);
                        helper.setTo(aluno.getEmail());
                        helper.setSubject("Convocatória: " + convite.getEvento());

                        // Construção do corpo do email em HTML
                        String htmlContent = "<div style='font-family: Arial, sans-serif; color: #333; max-width: 600px; margin: 0 auto; border: 1px solid #eee; padding: 20px;'>"
                                        +
                                        "<h2 style='color: #2c3e50; text-align: center;'>Convocatória de Aluno</h2>" +
                                        "<p>Olá <strong>" + aluno.getNomeCompleto() + "</strong>,</p>" +
                                        "<p>Foste convocado para participar no evento: <br><span style='font-size: 1.2em; font-weight: bold; color: #e67e22;'>"
                                        + convite.getEvento() + "</span></p>" +
                                        "<p style='margin-bottom: 30px;'>Por favor, confirma a tua disponibilidade clicando num dos botões abaixo:</p>"
                                        +

                                        // Layout dos Botões
                                        "<div style='text-align: center; margin-bottom: 40px;'>" +
                                        "<a href='" + linkConfirmar
                                        + "' style='background-color: #27ae60; color: white; padding: 15px 25px; text-decoration: none; border-radius: 5px; font-weight: bold; margin-right: 10px; display: inline-block;'>VOU PARTICIPAR</a>"
                                        +
                                        "<a href='" + linkRecusar
                                        + "' style='background-color: #c0392b; color: white; padding: 15px 25px; text-decoration: none; border-radius: 5px; font-weight: bold; display: inline-block;'>NÃO POSSO IR</a>"
                                        +
                                        "</div>" +

                                        "<p style='font-size: 0.8em; color: #bdc3c7; margin-top: 10px;'>Esta é uma mensagem automática da App de Gestão do CoreoFlow, por favor não responda.</p>"
                                        +
                                        assinatura.html(aluno.getStudio()) +
                                        "</div>";

                        helper.setText(htmlContent, true); // O 'true' indica que é HTML

                        mailSender.send(message);

                } catch (MessagingException e) {
                        // Log do erro (ex: Logger.error("Erro ao enviar email", e))
                        throw new RuntimeException("Falha ao enviar email de convocatória", e);
                }
        }

        @Async(AsyncEmailConfig.EMAIL_EXECUTOR)
        public void notificarProfessorConfirmacao(Aluno aluno, Convite convite, Professor professor) {
                if (envioBloqueado(studioDe(aluno))) return;
                try {
                        MimeMessage message = mailSender.createMimeMessage();
                        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

                        helper.setFrom(mailFrom, "CoreoFlow");
                        helper.setTo(professor.getEmail());
                        helper.setSubject("Confirmação de Presença: " + aluno.getNomeCompleto());

                        // Se for uma Competição, o estúdio recebe cópia oculta (Bcc) — não
                        // expõe o endereço do estúdio ao professor destinatário.
                        if (convite.getEvento() != null && convite.getEvento().contains("Competição")) {
                                helper.addBcc(emailAdmin(studioDe(aluno)));
                        }

                        String htmlContent = "<div style='font-family: Arial, sans-serif; color: #333; padding: 20px; border: 1px solid #eee;'>"
                                        +
                                        "<h2 style='color: #27ae60;'>Nova Confirmação de Aluno</h2>" +
                                        "<p>Olá Professor <strong>" + professor.getNome() + "</strong>,</p>" +
                                        "<p>O aluno <strong>" + aluno.getNomeCompleto()
                                        + "</strong> acabou de confirmar a presença no evento:</p>" +
                                        "<p style='font-size: 1.1em; font-weight: bold; color: #2c3e50;'>"
                                        + convite.getEvento() + "</p>" +
                                        "<hr style='border: 0; border-top: 1px solid #eee; margin: 20px 0;'>" +
                                        "<p style='font-size: 0.8em; color: #7f8c8d;'>Mensagem automática do Sistema CoreoFlow.</p>"
                                        +
                                        "</div>";

                        helper.setText(htmlContent + assinatura.html(studioDe(aluno)), true);
                        mailSender.send(message);

                } catch (Exception e) {
                        System.err.println("Erro ao notificar professor/competição: " + e.getMessage());
                }
        }

        @Async(AsyncEmailConfig.EMAIL_EXECUTOR)
        public void enviarEmailNotificacao(Aluno aluno) {
                Studio studio = studioDe(aluno);
                if (envioBloqueado(studio)) return;

                String corpo = String.format(
                                "<div style='font-family: Arial, sans-serif; color: #333;'>" +
                                                "<p>Olá,</p>" +
                                                "<p>Uma nova ficha de inscrição foi submetida com sucesso no CoreoFlow.</p>" +
                                                "<ul style='background-color: #f2f2f2; padding: 15px; border-left: 4px solid #666; list-style-type: none;'>" +
                                                "  <li><b>Nome:</b> %s</li>" +
                                                "  <li><b>NIF:</b> %s</li>" +
                                                "  <li><b>E-mail de contacto:</b> %s</li>" +
                                                "</ul>" +
                                                "<p>Por favor, aceda à área de administração e consulte o menu 'Validar Inscrições' para efetuar o tratamento e validação deste processo.</p>" +
                                                "<p style='font-size: 0.8em; color: #999;'>Mensagem gerada automaticamente pela plataforma CoreoFlow.</p>" +
                                                "</div>",
                                aluno.getNomeCompleto(),
                                aluno.getNumeroContribuinte(),
                                aluno.getEmail());

                enviarNotificacaoInterna(studio, "Nova Inscrição Pendente: " + aluno.getNomeCompleto(), corpo);
        }

        @Async(AsyncEmailConfig.EMAIL_EXECUTOR)
        public void enviarEmailNotificacaoRenovacao(Aluno aluno) {
                Studio studio = studioDe(aluno);
                if (envioBloqueado(studio)) return;

                String corpo = String.format(
                                "<div style='font-family: Arial, sans-serif; color: #333;'>" +
                                                "<p>Olá,</p>" +
                                                "<p>Um pedido de renovação de matrícula foi submetido no CoreoFlow.</p>" +
                                                "<ul style='background-color: #f2f2f2; padding: 15px; border-left: 4px solid #666; list-style-type: none;'>" +
                                                "  <li><b>Nome:</b> %s</li>" +
                                                "  <li><b>E-mail de contacto:</b> %s</li>" +
                                                "</ul>" +
                                                "<p>Por favor, aceda à área de administração e consulte o menu 'Validar Inscrições' para efetuar o tratamento e validação deste processo.</p>" +
                                                "<p style='font-size: 0.8em; color: #999;'>Mensagem gerada automaticamente pela plataforma CoreoFlow.</p>" +
                                                "</div>",
                                aluno.getNomeCompleto(),
                                aluno.getEmail());

                enviarNotificacaoInterna(studio, "Pedido de Renovação de Matrícula: " + aluno.getNomeCompleto(), corpo);
        }

        /** Email de confirmação enviado diretamente ao candidato após submeter um pedido (inscrição ou renovação). */
        @Async(AsyncEmailConfig.EMAIL_EXECUTOR)
        public void enviarEmailConfirmacaoCandidato(Aluno aluno, String nomeEstudio) {
                if (aluno.getEmail() == null || aluno.getEmail().isBlank()) return;
                if (envioBloqueado(studioDe(aluno))) return;
                try {
                        String html = "<div style='font-family:Arial,Helvetica,sans-serif;color:#333;max-width:600px;'>"
                                        + "<p>Olá " + escapeHtml(aluno.getNomeCompleto()) + ",</p>"
                                        + "<p>Recebemos o teu pedido com sucesso e entraremos em contacto muito em breve.</p>"
                                        + "<p>Obrigado,<br>" + escapeHtml(nomeEstudio) + "</p>"
                                        + "</div>";
                        enviarHtmlComAssinatura(aluno.getEmail(), studioDe(aluno), nomeEstudio,
                                        "Recebemos o teu pedido — " + nomeEstudio, html);
                } catch (Exception ex) {
                        System.err.println("Falha ao enviar e-mail de confirmação ao candidato: " + ex.getMessage());
                }
        }

        /** Uma turma na qual o aluno ficou inscrito, para o email de boas-vindas. */
        public record TurmaInscrita(String nome, String professor, String horario, double mensalidade) {
        }

        /**
         * Email de boas-vindas enviado ao aluno/encarregado quando a inscrição é
         * ativada pela secretaria: dá as boas-vindas e resume as turmas, horários,
         * professores e a mensalidade de cada uma. Vai assinado com o logo do
         * estúdio + "powered by CoreoFlow" e com Reply-To para o estúdio.
         */
        @Async(AsyncEmailConfig.EMAIL_EXECUTOR)
        public void enviarBoasVindasAluno(Aluno aluno, Studio studio, java.util.List<TurmaInscrita> turmas,
                        double totalMensal) {
                if (aluno == null || aluno.getEmail() == null || aluno.getEmail().isBlank()) return;
                if (turmas == null || turmas.isEmpty()) return;
                if (envioBloqueado(studio)) return;

                String nomeEstudio = studio != null && studio.getNome() != null ? studio.getNome() : "CoreoFlow";
                String nome = aluno.getNomeCompleto() != null ? aluno.getNomeCompleto().trim() : "";
                String primeiroNome = nome.isBlank() ? "" : nome.split("\\s+")[0];

                try {
                        MimeMessage mimeMessage = mailSender.createMimeMessage();
                        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");
                        helper.setFrom(mailFrom, nomeEstudio);
                        helper.setTo(aluno.getEmail());
                        helper.setReplyTo(emailAdmin(studio));
                        helper.setSubject("Bem-vindo(a) ao " + nomeEstudio + "!");

                        StringBuilder linhas = new StringBuilder();
                        for (TurmaInscrita t : turmas) {
                                String horario = t.horario() == null || t.horario().isBlank()
                                                ? "Horário a combinar" : t.horario();
                                String prof = t.professor() != null && !t.professor().isBlank()
                                                ? "<br><span style='color:#888;font-size:13px;'>com "
                                                                + t.professor() + "</span>"
                                                : "";
                                linhas.append("<tr>")
                                                .append("<td style='padding:10px 8px;border-bottom:1px solid #eee;'><b>")
                                                .append(t.nome()).append("</b>").append(prof).append("</td>")
                                                .append("<td style='padding:10px 8px;border-bottom:1px solid #eee;color:#555;'>")
                                                .append(horario).append("</td>")
                                                .append("<td style='padding:10px 8px;border-bottom:1px solid #eee;text-align:right;white-space:nowrap;'>")
                                                .append(String.format("%.2f €", t.mensalidade())).append("/mês</td>")
                                                .append("</tr>");
                        }

                        String totalRow = turmas.size() > 1
                                        ? "<tr><td colspan='2' style='padding:10px 8px;text-align:right;font-weight:bold;'>Total mensal</td>"
                                                        + "<td style='padding:10px 8px;text-align:right;font-weight:bold;white-space:nowrap;'>"
                                                        + String.format("%.2f €", totalMensal) + "/mês</td></tr>"
                                        : "";

                        String portal = baseUrl.replaceFirst("^https?://", "");

                        String html = "<div style='font-family:Arial,Helvetica,sans-serif;color:#333;max-width:600px;'>"
                                        + "<p>Olá " + primeiroNome + ",</p>"
                                        + "<p>É com muito gosto que te damos as boas-vindas ao <b>" + nomeEstudio
                                        + "</b>! A tua inscrição está confirmada e já tens lugar garantido"
                                        + (turmas.size() > 1 ? " nas seguintes turmas:" : " na seguinte turma:") + "</p>"
                                        + "<table style='border-collapse:collapse;width:100%;margin:18px 0;'>"
                                        + "<thead><tr style='background:#f5f5f5;'>"
                                        + "<th style='padding:10px 8px;text-align:left;'>Turma</th>"
                                        + "<th style='padding:10px 8px;text-align:left;'>Horário</th>"
                                        + "<th style='padding:10px 8px;text-align:right;'>Mensalidade</th>"
                                        + "</tr></thead><tbody>" + linhas + totalRow + "</tbody></table>"
                                        + "<p>No portal em <a href='" + baseUrl + "'>" + portal + "</a> podes acompanhar "
                                        + "presenças, mensalidades, avaliações, contratos e os vídeos das aulas.</p>"
                                        + "<p>Se tiveres qualquer dúvida, é só responderes a este email &mdash; estamos cá para ajudar.</p>"
                                        + "<p>Até já!</p>"
                                        + "</div>";

                        helper.setText(html + assinatura.html(studio), true);
                        mailSender.send(mimeMessage);
                        System.out.println("Email de boas-vindas enviado para " + aluno.getEmail());
                } catch (Exception ex) {
                        System.err.println("Falha ao enviar email de boas-vindas a " + aluno.getEmail() + ": "
                                        + ex.getMessage());
                }
        }

        /**
         * Convite para o portal do aluno/encarregado: link onde o destinatário
         * define a sua password. Usado tanto no primeiro acesso como na
         * reposição de password.
         */
        @Async(AsyncEmailConfig.EMAIL_EXECUTOR)
        public void enviarConvitePortal(String email, Studio studio, String linkAtivacao) {
                if (email == null || email.isBlank()) return;
                if (envioBloqueado(studio)) return;
                String nomeEstudio = studio != null && studio.getNome() != null ? studio.getNome() : "CoreoFlow";
                String portal = baseUrl.replaceFirst("^https?://", "");
                try {
                        String html = "<div style='font-family:Arial,Helvetica,sans-serif;color:#333;max-width:600px;'>"
                                        + "<p>Olá,</p>"
                                        + "<p>Foi criado um acesso ao portal do <b>" + escapeHtml(nomeEstudio)
                                        + "</b> para o email <b>" + escapeHtml(email) + "</b>.</p>"
                                        + "<p>No portal pode acompanhar presenças, mensalidades, avaliações, contratos "
                                        + "e os vídeos das aulas dos seus educandos.</p>"
                                        + "<p style='margin:24px 0;'>"
                                        + "<a href='" + linkAtivacao + "' style='background:#4A90E2;color:#fff;padding:12px 24px;"
                                        + "text-decoration:none;border-radius:6px;display:inline-block;font-weight:bold;'>"
                                        + "Definir palavra-passe</a></p>"
                                        + "<p style='font-size:13px;color:#888;'>O link é válido por 7 dias. Se o botão não "
                                        + "funcionar, copia este endereço para o navegador:<br>" + linkAtivacao + "</p>"
                                        + "<p style='font-size:13px;color:#888;'>Depois entra em <a href='" + baseUrl + "'>"
                                        + portal + "</a> com o seu email e a palavra-passe que definir.</p>"
                                        + "<p style='font-size:12px;color:#bbb;'>Se não esperava este email, pode ignorá-lo.</p>"
                                        + "</div>";
                        enviarHtmlComAssinatura(email, studio, nomeEstudio, "Acesso ao portal — " + nomeEstudio, html);
                        System.out.println("Convite do portal enviado para " + email + " (from=" + mailFrom + ")");
                } catch (Exception ex) {
                        System.err.println("Falha ao enviar convite do portal para " + email + ": " + ex.getMessage());
                }
        }

        /** Email enviado ao aluno quando a renovação de matrícula é aprovada na validação. */
        @Async(AsyncEmailConfig.EMAIL_EXECUTOR)
        public void enviarEmailAprovacaoRenovacao(Aluno aluno) {
                if (aluno.getEmail() == null || aluno.getEmail().isBlank()) return;
                if (envioBloqueado(studioDe(aluno))) return;
                try {
                        Studio studio = studioDe(aluno);
                        String nomeEstudio = studio != null && studio.getNome() != null ? studio.getNome() : "CoreoFlow";
                        String html = "<div style='font-family:Arial,Helvetica,sans-serif;color:#333;max-width:600px;'>"
                                        + "<p>Olá " + escapeHtml(aluno.getNomeCompleto()) + ",</p>"
                                        + "<p>A tua renovação de matrícula foi confirmada. Estás inscrito para o novo período.</p>"
                                        + "<p>Bom trabalho e até breve!</p>"
                                        + "</div>";
                        enviarHtmlComAssinatura(aluno.getEmail(), studio, nomeEstudio,
                                        "Renovação de matrícula confirmada", html);
                } catch (Exception ex) {
                        System.err.println("Falha ao enviar e-mail de aprovação de renovação: " + ex.getMessage());
                }
        }

        @Async(AsyncEmailConfig.EMAIL_EXECUTOR)
        public void enviarEmailNotificacaoExperimental(Aluno aluno, Turma turma) {
                Studio studio = studioDe(aluno);
                if (envioBloqueado(studio)) return;

                String corpo = String.format(
                                "<div style='font-family: Arial, sans-serif; color: #333;'>" +
                                                "<p>Olá,</p>" +
                                                "<p>Um aluno experimental foi adicionado pelo professor.</p>" +
                                                "<ul style='background-color: #f2f2f2; padding: 15px; border-left: 4px solid #666; list-style-type: none;'>" +
                                                "  <li><b>Nome:</b> %s</li>" +
                                                "  <li><b>Nº Telefone:</b> %s</li>" +
                                                "  <li><b>Turma:</b> %s</li>" +
                                                "</ul>" +
                                                "<p>Por favor, aceda à área de administração e consulte o menu 'Validar Inscrições' para efetuar o tratamento e validação deste processo.</p>" +
                                                "<p style='font-size: 0.8em; color: #999;'>Mensagem gerada automaticamente pela plataforma CoreoFlow.</p>" +
                                                "</div>",
                                aluno.getNomeCompleto(),
                                aluno.getTelemovel(),
                                turma != null ? turma.getDescricao() : "N/A");

                enviarNotificacaoInterna(studio, "Novo Aluno Experimental: " + aluno.getNomeCompleto(), corpo);
        }

        @Async(AsyncEmailConfig.EMAIL_EXECUTOR)
        public void enviarEmailNotificacaoProfessor(String emailDestinatario, Aluno aluno, String assunto,
                        String corpo) {
                if (envioBloqueado(studioDe(aluno))) return;
                try {
                        String html = "<div style='font-family:Arial,Helvetica,sans-serif;color:#333;max-width:600px;'>"
                                        + corpo.replace("\n", "<br>") + "</div>";
                        enviarHtmlComAssinatura(emailDestinatario, studioDe(aluno), "CoreoFlow", assunto, html);
                } catch (Exception ex) {
                        // Log do erro silencioso para não quebrar a experiência do utilizador final se
                        // o SMTP falhar
                        System.err.println("Falha ao enviar e-mail de notificação de inscrição: " + ex.getMessage());
                }
        }

        // 1. Lançamento inicial: Notifica ambos em simultâneo
        public void notificarAssinantesNovaTransferencia(String desc, String valor, String destino, String email1,
                        String email2) {
                if (envioBloqueado(studioDaSessao())) return;
                String texto = "Nova transferência registada a aguardar validação: " + desc + " | Valor: " + valor
                                + "€ para " + destino
                                + "\nPor favor, aceda ao netbanco para assinar e à plataforma para confirmar.";
                enviarEmailInterno(email1, "Aguardar Assinatura - Tesouraria", texto);
                enviarEmailInterno(email2, "Aguardar Assinatura - Tesouraria", texto);
        }

        // 2. Notificação intermédia: Um assinou, alerta o outro para fechar o circuito
        public void notificarSegundoAssinanteFaltaUma(String desc, String valor, String emailDestinatarioFalta,
                        String quemJaAssinou) {
                if (envioBloqueado(studioDaSessao())) return;
                String texto = "A transferência '" + desc + "' (" + valor + "€) já foi validada por " + quemJaAssinou
                                + ".\nFalta a sua assinatura para o documento ser pago.";
                enviarEmailInterno(emailDestinatarioFalta, "Falta a sua Assinatura - Tesouraria", texto);
        }

        // 3. Circuito fechado: Notifica a carga de que pode pagar
        public void notificarGeralProntoParaPagamento(String desc, String valor, String emailGeral) {
                if (envioBloqueado(studioDaSessao())) return;
                String texto = "A transferência '" + desc + "' de " + valor
                                + "€ recolheu ambas as assinaturas e está paga.";
                enviarEmailInterno(emailGeral, "Transferência Autorizada para Pagamento", texto);
        }

        // 4. Pagamento efetuado
        public void notificarEnvolvidosPagamentoEfetuado(String desc, String valor, String emailGeral, String email1,
                        String email2) {
                if (envioBloqueado(studioDaSessao())) return;
                String texto = "Pagamento concluído com sucesso: " + desc + " (" + valor + "€). Comprovativo anexado.";
                enviarEmailInterno(emailGeral, "Liquidação Concluída", texto);
                enviarEmailInterno(email1, "Liquidação Concluída", texto);
                enviarEmailInterno(email2, "Liquidação Concluída", texto);
        }

        private static String escapeHtml(String s) {
                if (s == null) return "";
                return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;");
        }

        public void enviarEmailInterno(String to, String subject, String body) {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setTo(to);
                message.setSubject(subject);
                message.setText(body);
                message.setFrom(mailFrom); // Ou o e-mail configurado no teu mailSender

                mailSender.send(message);
        }

        @org.springframework.beans.factory.annotation.Value("${app.suporte.email:dfc.daniel@gmail.com}")
        private String emailSuporteDefault;

        /** Destino configurável (via superadmin). Chamado pelo SuporteService. */
        public void enviarEmailSuporte(String destino, String bcc, String studio, String utilizador,
                        String emailUtilizador, String tipoProbema, String assunto, String descricao) {
                SimpleMailMessage msg = new SimpleMailMessage();
                msg.setFrom(mailFrom);
                msg.setTo(destino != null && !destino.isBlank() ? destino : emailSuporteDefault);
                if (bcc != null && !bcc.isBlank()) {
                        msg.setBcc(bcc.trim());
                }
                if (emailUtilizador != null && !emailUtilizador.isBlank()) {
                        msg.setReplyTo(emailUtilizador.trim());
                }
                msg.setSubject("[Suporte CoreoFlow] " + assunto);
                msg.setText(
                        "Estúdio: " + studio + "\n" +
                        "Utilizador: " + utilizador + (emailUtilizador != null ? " <" + emailUtilizador + ">" : "") + "\n" +
                        "Tipo de problema: " + tipoProbema + "\n" +
                        "Data: " + java.time.LocalDateTime.now() + "\n\n" +
                        "Descrição:\n" + descricao
                );
                mailSender.send(msg);
        }

        /** Resposta do suporte enviada ao cliente. */
        public void enviarRespostaSuporte(String paraEmail, String bcc, String nomeRemetente, String assuntoOriginal,
                        String textoResposta, String descricaoOriginal) throws Exception {
                jakarta.mail.internet.MimeMessage m = mailSender.createMimeMessage();
                org.springframework.mail.javamail.MimeMessageHelper h =
                                new org.springframework.mail.javamail.MimeMessageHelper(m, true, "UTF-8");
                h.setFrom(mailFrom, nomeRemetente != null && !nomeRemetente.isBlank() ? nomeRemetente : "CoreoFlow");
                h.setTo(paraEmail);
                if (bcc != null && !bcc.isBlank()) {
                        h.setBcc(bcc.trim());
                }
                h.setSubject("Re: " + assuntoOriginal);
                String html = "<div style=\"font-family:Arial,Helvetica,sans-serif;color:#333;max-width:560px\">"
                                + "<p style=\"font-size:15px;line-height:1.55;white-space:pre-wrap\">"
                                + esc(textoResposta) + "</p>"
                                + "<hr style=\"border:none;border-top:1px solid #eee;margin:18px 0\">"
                                + "<p style=\"font-size:12px;color:#999\">Em resposta ao teu pedido:<br>"
                                + "<i>" + esc(descricaoOriginal != null ? descricaoOriginal : "") + "</i></p>"
                                + "</div>";
                h.setText(html, true);
                mailSender.send(m);
        }

        private static String esc(String s) {
                return s == null ? "" : s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
        }
}