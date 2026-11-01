package dev.aurora;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import org.springframework.stereotype.Repository;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

/**
 * Camadas de cada módulo (doc 09, 9.8; ADR 0001 e 0031): controller → service →
 * repository, com o modelo compartilhado. SQL só em repositório. As fronteiras entre
 * módulos ficam com o Spring Modulith ({@link ModularidadeTest}).
 */
class ArquiteturaTest {

	private static final String CONTROLLER = "..controller..";

	private static final String SERVICE = "..service..";

	private static final String REPOSITORY = "..repository..";

	private static final String MODEL = "..model..";

	/** Configuração do acesso ao banco (contexto do tenant em cada conexão). */
	private static final String CONFIG = "..config..";

	private static JavaClasses classes;

	@BeforeAll
	static void importar() {
		classes = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
			.importPackages("dev.aurora");
	}

	@Test
	void controllerNaoAcessaRepositorio() {
		noClasses().that()
			.resideInAPackage(CONTROLLER)
			.should()
			.dependOnClassesThat()
			.resideInAPackage(REPOSITORY)
			.because("controller fala com o service; regra e transação ficam no service")
			.check(classes);
	}

	@Test
	void sqlSoEmRepositorio() {
		noClasses().that()
			.resideOutsideOfPackages(REPOSITORY, CONFIG)
			.and()
			.areNotAnnotatedWith(Repository.class)
			.should()
			.dependOnClassesThat()
			.haveFullyQualifiedName("org.springframework.jdbc.core.simple.JdbcClient")
			.orShould()
			.dependOnClassesThat()
			.resideInAPackage("org.springframework.jdbc.core..")
			.orShould()
			.dependOnClassesThat()
			.haveFullyQualifiedName("java.sql.Connection")
			.because("SQL fica nos repositórios (nunca em controller ou service)")
			.check(classes);
	}

	@Test
	void serviceNaoDependeDeController() {
		noClasses().that()
			.resideInAPackage(SERVICE)
			.should()
			.dependOnClassesThat()
			.resideInAPackage(CONTROLLER)
			.check(classes);
	}

	@Test
	void repositorioNaoDependeDeServiceNemDeController() {
		noClasses().that()
			.resideInAPackage(REPOSITORY)
			.should()
			.dependOnClassesThat()
			.resideInAnyPackage(SERVICE, CONTROLLER)
			.check(classes);
	}

	@Test
	void modeloNaoDependeDeOutrasCamadas() {
		noClasses().that()
			.resideInAPackage(MODEL)
			.should()
			.dependOnClassesThat()
			.resideInAnyPackage(SERVICE, CONTROLLER, REPOSITORY)
			.check(classes);
	}

	@Test
	void endpointsFicamNosControllers() {
		classes().that().areAnnotatedWith(RestController.class).should().resideInAPackage(CONTROLLER).check(classes);
	}

	@Test
	void repositoriosFicamNaCamadaDeRepositorio() {
		classes().that()
			.areAnnotatedWith(Repository.class)
			.should()
			.resideInAPackage(REPOSITORY)
			.orShould()
			.haveFullyQualifiedName("dev.aurora.comum.TarefasAgendadas")
			.because("a trava de tarefas é infraestrutura compartilhada (sem módulo)")
			.check(classes);
	}

}
