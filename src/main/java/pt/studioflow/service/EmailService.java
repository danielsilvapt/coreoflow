package pt.studioflow.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import pt.studioflow.model.Aluno;
import pt.studioflow.model.Convite;
import pt.studioflow.model.Professor;
import pt.studioflow.model.Turma;

import java.util.ArrayList;
import java.util.List;

@Service
public class EmailService {

    @org.springframework.beans.factory.annotation.Value("${spring.mail.username}")
    private String mailFrom; // Email remetente (sobreposto por Studio)

        @Autowired
        private JavaMailSender mailSender;

        public void enviarEmailParaLista(Professor professorCorrespondente, List<String> destinatarios, String assunto,
                        String corpoMensagem)
                        throws Exception {
                MimeMessage mimeMessage = mailSender.createMimeMessage();
                // Usamos o Helper para permitir HTML (assinatura com negrito, links, etc)
                MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

                helper.setFrom(mailFrom, "CoreoFlow");

                // Alunos como destinatários ocultos (privacidade entre alunos)
                helper.setBcc(destinatarios.toArray(new String[0]));

                // Em cópia: apenas o email do estúdio e o professor da modalidade
                List<String> emailsCc = new ArrayList<>();
                emailsCc.add(mailFrom);
                if (professorCorrespondente != null && professorCorrespondente.getEmail() != null
                                && !professorCorrespondente.getEmail().isBlank()) {
                        emailsCc.add(professorCorrespondente.getEmail());
                }
                helper.setCc(emailsCc.toArray(new String[0]));

                helper.setReplyTo(mailFrom);
                helper.setSubject(assunto);

                // Montagem do HTML com a Assinatura
                String assinaturaHtml = "<br><br>" +
                                "--<br>" +
                                "<img src='cid:logoAssinatura' style='width: 500px; height: auto;'><br>";

                // Junta a mensagem escrita no portal com a assinatura
                // O replace("\n", "<br>") serve para manter as quebras de linha que o professor
                // fizer

                String conteudoCompleto = corpoMensagem.replace("\n", "<br>") + assinaturaHtml;

                helper.setText(conteudoCompleto, true); // O 'true' indica que é HTML

                ClassPathResource res = new ClassPathResource("static/images/assinatura_studio.png");
                helper.addInline("logoAssinatura", res);

                mailSender.send(mimeMessage);
        }

        public void enviarEmailAprovacaoSala(String destinatario, String nomeProfessor, String sala, String data,
                        String horaInicio, String horaFim) {
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

                        String assinaturaHtml = "<br><br>--<br><img src='cid:logoAssinatura' style='width: 500px; height: auto;'><br>";
                        helper.setText(corpoMensagem + assinaturaHtml, true);

                        ClassPathResource res = new ClassPathResource("static/images/assinatura_studio.png");
                        helper.addInline("logoAssinatura", res);

                        mailSender.send(mimeMessage);

                        System.out.println(
                                        "Email de aprovação enviado para " + destinatario + " - " + nomeProfessor
                                                        + " - " + sala + " - " + data
                                                        + " " + horaInicio + "-" + horaFim);

                } catch (Exception e) {
                        System.err.println("Erro ao enviar e-mail de aprovação: " + e.getMessage());
                }
        }

        public void notificarAdminNovoPedido(String nomeProfessor, String tipo, String turma, String sala, String data,
                        String horaInicio, String horaFim, String observacoes) {
                try {
                        MimeMessage mimeMessage = mailSender.createMimeMessage();
                        MimeMessageHelper helper = new MimeMessageHelper(mimeMessage, true, "UTF-8");

                        helper.setTo(mailFrom);
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
                                                        "  <a href='https://app.coreoflow.app/horario-salas' " +
                                                        "  style='background-color: #000; color: #fff; padding: 12px 25px; text-decoration: none; border-radius: 5px; display: inline-block;'>"
                                                        +
                                                        "  Abrir Aplicação para Aprovar</a>" +
                                                        "</p>" +
                                                        "</div>",
                                        nomeProfessor, tipo, turma, sala, data, horaInicio, horaFim, observacoes);

                        String assinaturaHtml = "<br><br>--<br><img src='cid:logoAssinatura' style='width: 500px; height: auto;'><br>";
                        helper.setText(corpoMensagem + assinaturaHtml, true);

                        ClassPathResource res = new ClassPathResource("static/images/assinatura_studio.png");
                        helper.addInline("logoAssinatura", res);

                        mailSender.send(mimeMessage);

                        System.out.println("Notificação de novo pedido enviada para o admin: " + nomeProfessor + " - "
                                        + sala);
                } catch (Exception e) {
                        System.err.println("Erro ao enviar notificação de admin: " + e.getMessage());
                }
        }

        public void enviarEmailRecusaSala(String destinatario, String nomeProfessor, String sala, String data,
                        String horaInicio, String horaFim) {
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

                        String assinaturaHtml = "<br><br>--<br><img src='cid:logoAssinatura' style='width: 500px; height: auto;'><br>";
                        helper.setText(corpoMensagem + assinaturaHtml, true);

                        ClassPathResource res = new ClassPathResource("static/images/assinatura_studio.png");
                        helper.addInline("logoAssinatura", res);

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
                String urlBase = "https://app.coreoflow.app/api/convocatoria";
                String linkConfirmar = urlBase + "/confirmar?alunoId=" + aluno.getId() + "&conviteId="
                                + convite.getId();
                String linkRecusar = urlBase + "/recusar?alunoId=" + aluno.getId() + "&conviteId=" + convite.getId();

                try {
                        MimeMessage message = mailSender.createMimeMessage();
                        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

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

                                        "<hr style='border: 0; border-top: 1px solid #eee;'>" +

                                        // Assinatura
                                        "<div style='margin-top: 20px; text-align: left;'>" +
                                        "<p style='margin: 0; font-size: 0.9em; color: #7f8c8d;'>Atenciosamente,</p>" +
                                        "<p style='margin: 5px 0; font-weight: bold; color: #2c3e50;'>CoreoFlow</p>"
                                        +
                                        // Aqui inseres a URL da tua imagem (deve estar num servidor público)
                                        "<img src='https://app.coreoflow.app/images/assinatura_studio.png' alt='CoreoFlow Logo' style='width: 400px; margin-top: 10px;'>"
                                        +
                                        "<p style='font-size: 0.8em; color: #bdc3c7; margin-top: 10px;'>Esta é uma mensagem automática da App de Gestão do CoreoFlow, por favor não responda.</p>"
                                        +
                                        "</div>" +
                                        "</div>";

                        helper.setText(htmlContent, true); // O 'true' indica que é HTML

                        mailSender.send(message);

                } catch (MessagingException e) {
                        // Log do erro (ex: Logger.error("Erro ao enviar email", e))
                        throw new RuntimeException("Falha ao enviar email de convocatória", e);
                }
        }

        public void notificarProfessorConfirmacao(Aluno aluno, Convite convite, Professor professor) {
                try {
                        MimeMessage message = mailSender.createMimeMessage();
                        MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");

                        helper.setTo(professor.getEmail());
                        helper.setSubject("Confirmação de Presença: " + aluno.getNomeCompleto());

                        // Lógica para adicionar CC se for uma Competição
                        if (convite.getEvento() != null && convite.getEvento().contains("Competição")) {
                                helper.addCc(mailFrom);
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

                        helper.setText(htmlContent, true);
                        mailSender.send(message);

                } catch (Exception e) {
                        System.err.println("Erro ao notificar professor/competição: " + e.getMessage());
                }
        }

        public void enviarEmailNotificacao(Aluno aluno) {
                try {
                        SimpleMailMessage mensagem = new SimpleMailMessage();
                        mensagem.setTo(mailFrom);
                        mensagem.setSubject("Nova Inscrição Pendente: " + aluno.getNomeCompleto());

                        String corpoEmail = String.format(
                                        "Olá,\n\n" +
                                                        "Uma nova ficha de inscrição foi submetida com sucesso no sistema da CoreoFlow.\n\n"
                                                        +
                                                        "Detalhes do Aluno:\n" +
                                                        "- Nome: %s\n" +
                                                        "- NIF: %s\n" +
                                                        "- E-mail de contacto: %s\n\n" +
                                                        "Por favor, aceda à área de administração e consulte o menu 'Validar Inscrições' para efetuar o tratamento e validação deste processo.\n\n"
                                                        +
                                                        "Mensagem gerada automaticamente pelo plataforma CoreoFlow.",
                                        aluno.getNomeCompleto(),
                                        aluno.getNumeroContribuinte(),
                                        aluno.getEmail());

                        mensagem.setText(corpoEmail);
                        mailSender.send(mensagem);

                } catch (Exception ex) {
                        // Log do erro silencioso para não quebrar a experiência do utilizador final se
                        // o SMTP falhar
                        System.err.println("Falha ao enviar e-mail de notificação de inscrição: " + ex.getMessage());
                }
        }

        public void enviarEmailNotificacaoRenovacao(Aluno aluno) {
                try {
                        SimpleMailMessage mensagem = new SimpleMailMessage();
                        mensagem.setTo(mailFrom);
                        mensagem.setSubject("Pedido de Renovação de Matrícula: " + aluno.getNomeCompleto());

                        String corpoEmail = String.format(
                                        "Olá,\n\n" +
                                                        "Um pedido de renovação de matrícula foi submetido no sistema da CoreoFlow.\n\n"
                                                        +
                                                        "Detalhes do Aluno:\n" +
                                                        "- Nome: %s\n" +
                                                        "- E-mail de contacto: %s\n\n" +
                                                        "Por favor, aceda à área de administração e consulte o menu 'Validar Inscrições' para efetuar o tratamento e validação deste processo.\n\n"
                                                        +
                                                        "Mensagem gerada automaticamente pelo plataforma CoreoFlow.",
                                        aluno.getNomeCompleto(),
                                        aluno.getEmail());

                        mensagem.setText(corpoEmail);
                        mailSender.send(mensagem);

                } catch (Exception ex) {
                        System.err.println("Falha ao enviar e-mail de notificação de renovação: " + ex.getMessage());
                }
        }

        /** Email de confirmação enviado diretamente ao candidato após submeter um pedido (inscrição ou renovação). */
        public void enviarEmailConfirmacaoCandidato(Aluno aluno, String nomeEstudio) {
                if (aluno.getEmail() == null || aluno.getEmail().isBlank()) return;
                try {
                        SimpleMailMessage mensagem = new SimpleMailMessage();
                        mensagem.setTo(aluno.getEmail());
                        mensagem.setSubject("Recebemos o teu pedido — " + nomeEstudio);

                        String corpoEmail = String.format(
                                        "Olá %s,\n\n" +
                                                        "Recebemos o teu pedido com sucesso e entraremos em contacto muito em breve.\n\n"
                                                        +
                                                        "Obrigado,\n%s",
                                        aluno.getNomeCompleto(), nomeEstudio);

                        mensagem.setText(corpoEmail);
                        mailSender.send(mensagem);

                } catch (Exception ex) {
                        System.err.println("Falha ao enviar e-mail de confirmação ao candidato: " + ex.getMessage());
                }
        }

        public void enviarEmailNotificacaoExperimental(Aluno aluno, Turma turma) {
                try {
                        SimpleMailMessage mensagem = new SimpleMailMessage();
                        mensagem.setTo(mailFrom);
                        mensagem.setSubject("Novo Aluno Experimental: " + aluno.getNomeCompleto());

                        String corpoEmail = String.format(
                                        "Olá,\n\n" +
                                                        "Um aluno experimental foi adicionado pelo professor.\n\n"
                                                        +
                                                        "Detalhes do Aluno:\n" +
                                                        "- Nome: %s\n" +
                                                        "- Nº Telefone: %s\n" +
                                                        "- Turma: %s\n\n" +
                                                        "Por favor, aceda à área de administração e consulte o menu 'Validar Inscrições' para efetuar o tratamento e validação deste processo.\n\n"
                                                        +
                                                        "Mensagem gerada automaticamente pelo plataforma CoreoFlow.",
                                        aluno.getNomeCompleto(),
                                        aluno.getTelemovel(),
                                        turma != null ? turma.getDescricao() : "N/A");

                        mensagem.setText(corpoEmail);
                        mailSender.send(mensagem);

                } catch (Exception ex) {
                        // Log do erro silencioso para não quebrar a experiência do utilizador final se
                        // o SMTP falhar
                        System.err.println("Falha ao enviar e-mail de notificação de inscrição: " + ex.getMessage());
                }
        }

        public void enviarEmailNotificacaoProfessor(String emailDestinatario, Aluno aluno, String assunto,
                        String corpo) {
                try {
                        SimpleMailMessage mensagem = new SimpleMailMessage();
                        mensagem.setTo(emailDestinatario);
                        mensagem.setSubject(assunto);

                        mensagem.setText(corpo);

                        mailSender.send(mensagem);

                } catch (Exception ex) {
                        // Log do erro silencioso para não quebrar a experiência do utilizador final se
                        // o SMTP falhar
                        System.err.println("Falha ao enviar e-mail de notificação de inscrição: " + ex.getMessage());
                }
        }

        // 1. Lançamento inicial: Notifica ambos em simultâneo
        public void notificarAssinantesNovaTransferencia(String desc, String valor, String destino, String email1,
                        String email2) {
                String texto = "Nova transferência registada a aguardar validação: " + desc + " | Valor: " + valor
                                + "€ para " + destino
                                + "\nPor favor, aceda ao netbanco para assinar e à plataforma para confirmar.";
                enviarEmailInterno(email1, "Aguardar Assinatura - Tesouraria", texto);
                enviarEmailInterno(email2, "Aguardar Assinatura - Tesouraria", texto);
        }

        // 2. Notificação intermédia: Um assinou, alerta o outro para fechar o circuito
        public void notificarSegundoAssinanteFaltaUma(String desc, String valor, String emailDestinatarioFalta,
                        String quemJaAssinou) {
                String texto = "A transferência '" + desc + "' (" + valor + "€) já foi validada por " + quemJaAssinou
                                + ".\nFalta a sua assinatura para o documento ser pago.";
                enviarEmailInterno(emailDestinatarioFalta, "Falta a sua Assinatura - Tesouraria", texto);
        }

        // 3. Circuito fechado: Notifica a carga de que pode pagar
        public void notificarGeralProntoParaPagamento(String desc, String valor, String emailGeral) {
                String texto = "A transferência '" + desc + "' de " + valor
                                + "€ recolheu ambas as assinaturas e está paga.";
                enviarEmailInterno(emailGeral, "Transferência Autorizada para Pagamento", texto);
        }

        // 4. Pagamento efetuado
        public void notificarEnvolvidosPagamentoEfetuado(String desc, String valor, String emailGeral, String email1,
                        String email2) {
                String texto = "Pagamento concluído com sucesso: " + desc + " (" + valor + "€). Comprovativo anexado.";
                enviarEmailInterno(emailGeral, "Liquidação Concluída", texto);
                enviarEmailInterno(email1, "Liquidação Concluída", texto);
                enviarEmailInterno(email2, "Liquidação Concluída", texto);
        }

        public void enviarEmailInterno(String to, String subject, String body) {
                SimpleMailMessage message = new SimpleMailMessage();
                message.setTo(to);
                message.setSubject(subject);
                message.setText(body);
                message.setFrom(mailFrom); // Ou o e-mail configurado no teu mailSender

                mailSender.send(message);
        }

        @org.springframework.beans.factory.annotation.Value("${app.suporte.email}")
        private String emailSuporte;

        public void enviarEmailSuporte(String studio, String utilizador, String tipoProbema, String assunto, String descricao) {
                SimpleMailMessage msg = new SimpleMailMessage();
                msg.setFrom(mailFrom);
                msg.setTo(emailSuporte);
                msg.setSubject("[Suporte CoreoFlow] " + assunto);
                msg.setText(
                        "Estúdio: " + studio + "\n" +
                        "Utilizador: " + utilizador + "\n" +
                        "Tipo de problema: " + tipoProbema + "\n\n" +
                        "Descrição:\n" + descricao
                );
                mailSender.send(msg);
        }
}