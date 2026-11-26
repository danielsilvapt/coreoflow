package pt.studioflow.service;

import org.springframework.stereotype.Service;
import pt.studioflow.model.Idioma;

import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;

/**
 * Traduções PT/EN/FR dos formulários públicos (inscrição e renovação). Não
 * usa o mecanismo de i18n do Spring (MessageSource) porque o projeto não o
 * usa em mais nenhum sítio — um mapa simples chave→idioma é suficiente para
 * o número reduzido de strings destes formulários e mantém-se fácil de
 * estender com novas chaves.
 */
@Service
public class TranslationService {

    private static final Map<String, Map<Idioma, String>> TRADUCOES = new HashMap<>();

    private static void add(String key, String pt, String en, String fr) {
        Map<Idioma, String> m = new EnumMap<>(Idioma.class);
        m.put(Idioma.PT, pt);
        m.put(Idioma.EN, en);
        m.put(Idioma.FR, fr);
        TRADUCOES.put(key, m);
    }

    static {
        // Ficha de Inscrição
        add("inscricao.titulo", "Ficha de Inscrição", "Registration Form", "Fiche d'Inscription");
        add("inscricao.subtitulo", "Junte-se à nossa família de dança!", "Join our dance family!",
                "Rejoignez notre famille de danse !");
        add("inscricao.nomeCompleto", "Nome Completo", "Full Name", "Nom Complet");
        add("inscricao.dataNascimento", "Data de Nascimento", "Date of Birth", "Date de Naissance");
        add("inscricao.telemovel", "Telemóvel", "Phone", "Téléphone");
        add("inscricao.email", "E-mail", "Email", "E-mail");
        add("inscricao.morada", "Morada Completa", "Full Address", "Adresse Complète");
        add("inscricao.turmas", "Escolha as Turmas", "Choose Your Classes", "Choisissez vos Cours");
        add("inscricao.submeter", "Finalizar Inscrição", "Submit Registration", "Finaliser l'Inscription");
        add("inscricao.sucesso.titulo", "Enviado!", "Sent!", "Envoyé !");
        add("inscricao.sucesso.msg", " entrará em contacto muito em breve.",
                " will contact you very soon.", " vous contactera très bientôt.");

        add("inscricao.jaAluno", "Já é aluno do ", "Are you already a student at ", "Êtes-vous déjà élève de ");
        add("inscricao.jaAlunoObrigatorio", "Indica se já é aluno do estúdio.",
                "Please indicate whether you are already a student.", "Indiquez si vous êtes déjà élève.");
        add("inscricao.jaAlunoAjuda", "Já frequenta as aulas e só se está a registar na app: fica isento da taxa de inscrição.",
                "Already attending classes and just registering in the app: no registration fee.",
                "Vous suivez déjà les cours et vous vous inscrivez seulement sur l'app : pas de frais d'inscription.");
        add("inscricao.familiar", "Tenho familiares inscritos no ", "I have family members enrolled at ",
                "J'ai des membres de ma famille inscrits à ");
        add("inscricao.familiarDesconto", "desconto de familiares", "family discount", "réduction famille");

        // Saúde (inscrição e renovação)
        add("saude.pergunta", "O aluno tem algum problema de saúde?", "Does the student have any health condition?",
                "L'élève a-t-il un problème de santé ?");
        add("saude.sim", "Sim", "Yes", "Oui");
        add("saude.nao", "Não", "No", "Non");
        add("saude.qual", "Qual?", "Which one?", "Lequel ?");
        add("saude.placeholder", "Ex: asma, alergias, problemas cardíacos, lesões, medicação habitual...",
                "E.g. asthma, allergies, heart conditions, injuries, regular medication...",
                "Ex : asthme, allergies, problèmes cardiaques, blessures, traitement habituel...");
        add("saude.obrigatorio", "Indica se o aluno tem algum problema de saúde.",
                "Please indicate whether the student has any health condition.",
                "Indique si l'élève a un problème de santé.");
        // Autorização de imagens (inscrição e renovação)
        add("imagens.titulo", "Autorização de imagens", "Image authorisation", "Autorisation d'images");
        add("imagens.finalidade",
                "Todas as imagens têm por fim elaborar um registo ao longo do ano das atividades e promover o ",
                "All images are intended to record the year's activities and to promote ",
                "Toutes les images ont pour but de garder une trace des activités de l'année et de promouvoir ");
        add("imagens.captacao", "Captação de imagens pelo ", "Capture of images by ", "Prise d'images par ");
        add("imagens.captacaoMenor", ", nas quais possa estar envolvido o meu educando, no ano letivo ",
                ", in which my child may appear, during the school year ",
                ", sur lesquelles mon enfant peut apparaître, pendant l'année scolaire ");
        add("imagens.captacaoAdulto", ", nas quais eu possa estar envolvido(a), no ano letivo ",
                ", in which I may appear, during the school year ",
                ", sur lesquelles je peux apparaître, pendant l'année scolaire ");
        add("imagens.divulgacao",
                "Divulgação dessas imagens: 1) nos grupos privados de WhatsApp onde todos os pais estão envolvidos;"
                        + " 2) nas redes sociais (Facebook / Instagram) do ",
                "Sharing of those images: 1) in the private WhatsApp groups with all the parents;"
                        + " 2) on the social media (Facebook / Instagram) of ",
                "Diffusion de ces images : 1) dans les groupes WhatsApp privés réunissant tous les parents ;"
                        + " 2) sur les réseaux sociaux (Facebook / Instagram) de ");
        add("imagens.autorizo", "Autorizo", "I authorise", "J'autorise");
        add("imagens.naoAutorizo", "Não autorizo", "I do not authorise", "Je n'autorise pas");
        add("imagens.obrigatorio", "Indica se autoriza a captação de imagens.",
                "Please indicate whether you authorise image capture.",
                "Indiquez si vous autorisez la prise d'images.");
        add("imagens.divulgacaoObrigatorio", "Indica se autoriza a divulgação das imagens.",
                "Please indicate whether you authorise sharing the images.",
                "Indiquez si vous autorisez la diffusion des images.");

        add("saude.indicaQual","Indica qual o problema de saúde.", "Please specify the health condition.",
                "Indique le problème de santé.");

        // Renovação de Matrícula
        add("renovacao.titulo", "Renovação de Matrícula", "Enrollment Renewal", "Renouvellement d'Inscription");
        add("renovacao.subtitulo", "Continua connosco! Indica o teu email para começar.",
                "Stay with us! Enter your email to begin.", "Reste avec nous ! Indique ton email pour commencer.");
        add("renovacao.email", "O teu email", "Your email", "Ton email");
        add("renovacao.procurar", "Procurar", "Search", "Rechercher");
        add("renovacao.turmas", "Turmas", "Classes", "Cours");
        add("renovacao.submeter", "Submeter Pedido de Renovação", "Submit Renewal Request", "Soumettre la Demande");
        add("renovacao.sucesso.titulo", "Pedido enviado!", "Request sent!", "Demande envoyée !");
        add("renovacao.sucesso.msg", " entrará em contacto muito em breve.",
                " will contact you very soon.", " vous contactera très bientôt.");
    }

    public String t(String key, Idioma idioma) {
        Map<Idioma, String> m = TRADUCOES.get(key);
        if (m == null) return key;
        Idioma alvo = idioma != null ? idioma : Idioma.PT;
        return m.getOrDefault(alvo, m.get(Idioma.PT));
    }
}
