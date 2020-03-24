package net.jqwik.engine.execution.lifecycle;

import java.lang.annotation.*;
import java.lang.reflect.*;
import java.util.*;

import org.junit.platform.engine.*;

import net.jqwik.api.*;
import net.jqwik.api.lifecycle.*;
import net.jqwik.engine.*;
import net.jqwik.engine.descriptor.*;
import net.jqwik.engine.support.*;

import static org.assertj.core.api.Assertions.*;

import static net.jqwik.api.lifecycle.PropagationMode.*;
import static net.jqwik.engine.TestDescriptorBuilder.*;

class LifecycleRegistryTests {

	LifecycleHooksRegistry registry = new LifecycleHooksRegistry();

	@Example
	void globalHookIsFoundInAllElements() {
		TestDescriptor engine =
			forEngine(new JqwikTestEngine())
				.with(forClass(Container1.class, "method1_1", "method1_2"))
				.with(forClass(Container2.class, "method2_1", "method2_2"))
				.build();

		GlobalHook hookInstance = new GlobalHook();
		registry.registerLifecycleInstance(engine, hookInstance);

		assertThat(registry.hasHook(engine, GlobalHook.class));
		assertThat(engine.getDescendants()).allMatch(descriptor -> registry.hasHook(descriptor, GlobalHook.class));
	}

	@Example
	void globalHookForMethodsOnly() {
		TestDescriptor engine =
			forEngine(new JqwikTestEngine())
				.with(forClass(Container1.class, "method1_1", "method1_2"))
				.with(forClass(Container2.class, "method2_1", "method2_2"))
				.build();

		GlobalHookForMethodsOnly hookInstance = new GlobalHookForMethodsOnly();
		registry.registerLifecycleInstance(engine, hookInstance);

		assertThat(!registry.hasHook(engine, GlobalHookForMethodsOnly.class));
		assertThat(engine.getDescendants().stream().filter(descriptor -> descriptor instanceof PropertyMethodDescriptor))
			.allMatch(descriptor -> registry.hasHook(descriptor, GlobalHookForMethodsOnly.class));
		assertThat(engine.getDescendants().stream().filter(descriptor -> !(descriptor instanceof PropertyMethodDescriptor)))
			.allMatch(descriptor -> !registry.hasHook(descriptor, GlobalHookForMethodsOnly.class));
	}

	@Example
	void currentDescriptorIsSetDuringRegisteringHookClass() {
		TestDescriptor container1 = forClass(Container1.class, "method1_1", "method1_2").build();
		registry.registerLifecycleHook(container1, RememberCurrentDescriptorHook.class, NO_DESCENDANTS);

		assertThat(RememberCurrentDescriptorHook.currentDescriptor).isSameAs(container1);
	}

	@Group
	class UsingRegistrar {
		@Example
		void registerThroughRegistrar() {
			TestDescriptor container1 = forClass(Container1.class, "method1_1", "method1_2").build();
			registry.registerLifecycleHook(container1, RegisterGlobalHook.class, NO_DESCENDANTS);
			assertThat(registry.hasHook(container1, GlobalHook.class));
			assertThat(container1.getDescendants()).allMatch(child -> registry.hasHook(child, GlobalHook.class));
		}

		@Example
		void registerThroughRegistrar_withExplicitPropagation() {
			TestDescriptor container1 = forClass(Container1.class, "method1_1", "method1_2").build();
			registry.registerLifecycleHook(container1, RegisterGlobalHookNoDescendants.class, NO_DESCENDANTS);
			assertThat(registry.hasHook(container1, GlobalHook.class));
			assertThat(container1.getDescendants()).allMatch(child -> !registry.hasHook(child, GlobalHook.class));
		}
	}

	@Group
	@AddLifecycleHook(value = ChangeFirstParamTo42.class, propagateTo = ALL_DESCENDANTS)
	@AddLifecycleHook(value = ChangeSecondParamToAAA.class, propagateTo = DIRECT_DESCENDANTS)
	class Propagation {

		@Example
		void exampleInDirectDescendant(@ForAll int anInt, @ForAll String aString) {
			assertThat(anInt).isEqualTo(42);
			assertThat(aString).isEqualTo("AAA");
		}

		@Group
		class NestedPropagation {
			@Example
			void exampleInNestedDescendant(@ForAll int anInt, @ForAll String aString) {
				assertThat(anInt).isEqualTo(42);
				assertThat(aString).isNotEqualTo("AAA");
			}
		}
	}

	@Group
	@AddLifecycleHook(CheckPrepareForContainer.class)
	class Preparation {

		@Example
		void withGlobalHook_allElementsWillBePrepared() {
			TestDescriptor engine =
				forEngine(new JqwikTestEngine())
					.with(forClass(Container1.class, "method1_1", "method1_2"))
					.with(forClass(Container2.class, "method2_1", "method2_2"))
					.build();

			GlobalHook hookInstance = new GlobalHook();
			registry.registerLifecycleInstance(engine, hookInstance);

			LifecycleContext engineContext = createLifecycleContext("engine");
			registry.prepareHooks(engine, engineContext);
			assertThat(hookInstance.preparedContexts).containsExactly(engineContext);

			for (TestDescriptor descendant : engine.getDescendants()) {
				LifecycleContext childContext = createLifecycleContext(descendant.getDisplayName());
				registry.prepareHooks(descendant, childContext);
				assertThat(hookInstance.preparedContexts).contains(childContext);
			}
		}

		private LifecycleContext createLifecycleContext(String label) {
			return new LifecycleContext() {
				@Override
				public String label() {
					return label;
				}

				@Override
				public Optional<AnnotatedElement> optionalElement() {
					return Optional.empty();
				}

				@Override
				public Optional<Class<?>> optionalContainerClass() {
					return Optional.empty();
				}

				@Override
				public Reporter reporter() {
					return ((key, value) -> {});
				}

				@Override
				public <T extends Annotation> Optional<T> findAnnotation(Class<T> annotationClass) {
					return Optional.empty();
				}

				@Override
				public <T> T newInstance(Class<T> clazz) {
					return JqwikReflectionSupport.newInstanceWithDefaultConstructor(clazz);
				}

				@Override
				public Optional<ResolveParameterHook.ParameterSupplier> resolveParameter(Executable executable, int index) {
					return Optional.empty();
				}
			};
		}

		@Property(tries = 10)
		@AddLifecycleHook(CheckPrepareForProperty.class)
		void propertyMethod() {
		}

		private AnnotatedElement elementOf(TestDescriptor descendant) {
			if (descendant instanceof JqwikDescriptor) {
				return ((JqwikDescriptor) descendant).getAnnotatedElement();
			}
			return null;
		}

	}

	private static class Container1 {
		@Property
		public void method1_1() {}

		@Property
		public void method1_2() {}
	}

	private static class Container2 {
		@Property
		public void method2_1() {}

		@Property
		public void method2_2() {}
	}

	private static class GlobalHook implements LifecycleHook {
		List<LifecycleContext> preparedContexts = new ArrayList<>();

		@Override
		public void prepareFor(LifecycleContext lifecycleContext) {
			preparedContexts.add(lifecycleContext);
		}

		@Override
		public PropagationMode propagateTo() {
			return PropagationMode.ALL_DESCENDANTS;
		}
	}

	private static class RegisterGlobalHook implements RegistrarHook {
		@Override
		public void registerHooks(Registrar registrar) {
			registrar.register(GlobalHook.class);
		}
	}

	private static class RegisterGlobalHookNoDescendants implements RegistrarHook {
		@Override
		public void registerHooks(Registrar registrar) {
			registrar.register(GlobalHook.class, NO_DESCENDANTS);
		}
	}

	private static class GlobalHookForMethodsOnly implements LifecycleHook {
		@Override
		public boolean appliesTo(Optional<AnnotatedElement> optionalElement) {
			return optionalElement.map(element -> element instanceof Method).orElse(false);
		}

		@Override
		public PropagationMode propagateTo() {
			return PropagationMode.ALL_DESCENDANTS;
		}
	}

	static class RememberCurrentDescriptorHook implements LifecycleHook {
		static TestDescriptor currentDescriptor;

		RememberCurrentDescriptorHook() {
			this.currentDescriptor = CurrentTestDescriptor.get();
		}
	}

	static class CheckPrepareForProperty implements LifecycleHook, AroundPropertyHook {
		boolean prepareHasBeenCalled = false;

		@Override
		public void prepareFor(LifecycleContext lifecycleContext) {
			assertThat(CurrentTestDescriptor.get()).isInstanceOf(TestDescriptor.class);
			assertThat(lifecycleContext).isInstanceOf(PropertyLifecycleContext.class);
			prepareHasBeenCalled = true;
		}

		@Override
		public PropertyExecutionResult aroundProperty(PropertyLifecycleContext context, PropertyExecutor property) {
			try {
				return property.execute();
			} finally {
				assertThat(prepareHasBeenCalled).isTrue();
			}
		}

		@Override
		public PropagationMode propagateTo() {
			return PropagationMode.ALL_DESCENDANTS;
		}

	}

	static class CheckPrepareForContainer implements LifecycleHook, AfterContainerHook {
		boolean prepareHasBeenCalled = false;

		@Override
		public void prepareFor(LifecycleContext lifecycleContext) {
			assertThat(CurrentTestDescriptor.get()).isInstanceOf(TestDescriptor.class);
			assertThat(lifecycleContext).isInstanceOf(ContainerLifecycleContext.class);
			assertThat(lifecycleContext.optionalElement().get()).isInstanceOf(Class.class);
			prepareHasBeenCalled = true;
		}

		@Override
		public void afterContainer(ContainerLifecycleContext context) throws Throwable {
			assertThat(prepareHasBeenCalled).isTrue();
		}
	}

	static class ChangeFirstParamTo42 implements AroundTryHook {

		@Override
		public TryExecutionResult aroundTry(TryLifecycleContext context, TryExecutor aTry, List<Object> parameters) {
			if (parameters.size() >= 1) {
				parameters.set(0, 42);
			}
			return aTry.execute(parameters);
		}
	}

	static class ChangeSecondParamToAAA implements AroundTryHook {

		@Override
		public TryExecutionResult aroundTry(TryLifecycleContext context, TryExecutor aTry, List<Object> parameters) {
			if (parameters.size() >= 2) {
				parameters.set(1, "AAA");
			}
			return aTry.execute(parameters);
		}
	}
}
