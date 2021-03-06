/*
 * Copyright (c) Facebook, Inc. and its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.facebook.buck.core.rules.providers.impl;

import static org.hamcrest.junit.MatcherAssert.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

import com.facebook.buck.core.rules.providers.annotations.ImmutableInfo;
import com.facebook.buck.core.starlark.compatible.BuckStarlark;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.devtools.build.lib.syntax.Argument;
import com.google.devtools.build.lib.syntax.DictionaryLiteral;
import com.google.devtools.build.lib.syntax.Environment;
import com.google.devtools.build.lib.syntax.EvalException;
import com.google.devtools.build.lib.syntax.FuncallExpression;
import com.google.devtools.build.lib.syntax.Identifier;
import com.google.devtools.build.lib.syntax.IntegerLiteral;
import com.google.devtools.build.lib.syntax.Mutability;
import com.google.devtools.build.lib.syntax.SkylarkDict;
import com.google.devtools.build.lib.syntax.StringLiteral;
import java.lang.reflect.InvocationTargetException;
import java.util.Map;
import java.util.Set;
import org.hamcrest.Matchers;
import org.immutables.value.Value;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;

public class BuiltInProviderInfoTest {

  @Rule public ExpectedException thrown = ExpectedException.none();

  @ImmutableInfo(
      args = {"str", "my_info"},
      defaultSkylarkValues = {"\"default value\"", "1"})
  public abstract static class SomeInfo extends BuiltInProviderInfo<SomeInfo> {
    public static final BuiltInProvider<SomeInfo> PROVIDER =
        BuiltInProvider.of(ImmutableSomeInfo.class);

    public abstract String str();

    public abstract int myInfo();
  }

  @ImmutableInfo(
      args = {"str"},
      defaultSkylarkValues = {""})
  public abstract static class OtherInfo extends BuiltInProviderInfo<OtherInfo> {
    public static final BuiltInProvider<OtherInfo> PROVIDER =
        BuiltInProvider.of(ImmutableOtherInfo.class);

    public abstract String str();
  }

  @ImmutableInfo(
      args = {"set"},
      defaultSkylarkValues = "[]")
  public abstract static class InfoWithSet extends BuiltInProviderInfo<InfoWithSet> {
    public static final BuiltInProvider<InfoWithSet> PROVIDER =
        BuiltInProvider.of(ImmutableInfoWithSet.class);

    @Value.Parameter(order = 0)
    public abstract Set<String> set();
  }

  @ImmutableInfo(
      args = {"map"},
      defaultSkylarkValues = "{}")
  public abstract static class InfoWithMap extends BuiltInProviderInfo<InfoWithMap> {
    public static final BuiltInProvider<InfoWithMap> PROVIDER =
        BuiltInProvider.of(ImmutableInfoWithMap.class);

    @Value.Parameter(order = 0)
    public abstract SkylarkDict<String, Integer> map();
  }

  @ImmutableInfo(args = {"val"})
  public abstract static class InfoWithNoDefaultValOnAnnotation
      extends BuiltInProviderInfo<InfoWithNoDefaultValOnAnnotation> {
    public static final BuiltInProvider<InfoWithNoDefaultValOnAnnotation> PROVIDER =
        BuiltInProvider.of(ImmutableInfoWithNoDefaultValOnAnnotation.class);

    public abstract Integer val();
  }

  @ImmutableInfo(
      args = {"str_list", "my_info"},
      defaultSkylarkValues = {"{\"foo\":\"bar\"}", "1"})
  public abstract static class SomeInfoWithInstantiate
      extends BuiltInProviderInfo<SomeInfoWithInstantiate> {
    public static final BuiltInProvider<SomeInfoWithInstantiate> PROVIDER =
        BuiltInProvider.of(ImmutableSomeInfoWithInstantiate.class);

    public abstract ImmutableList<String> str_list();

    public abstract String myInfo();

    public static SomeInfoWithInstantiate instantiateFromSkylark(
        SkylarkDict<String, String> s, int myInfo) throws EvalException {
      Map<String, String> validated = s.getContents(String.class, String.class, "stuff");
      return new ImmutableSomeInfoWithInstantiate(
          ImmutableList.copyOf(validated.keySet()), Integer.toString(myInfo));
    }
  }

  @Test
  public void someInfoProviderCreatesCorrectInfo()
      throws IllegalAccessException, InstantiationException, InvocationTargetException,
          InterruptedException, EvalException {
    SomeInfo someInfo1 = new ImmutableSomeInfo("a", 1);
    assertEquals("a", someInfo1.str());
    assertEquals(1, someInfo1.myInfo());

    SomeInfo someInfo2 = someInfo1.getProvider().createInfo("b", 2);
    assertEquals("b", someInfo2.str());
    assertEquals(2, someInfo2.myInfo());

    SomeInfo someInfo3 = SomeInfo.PROVIDER.createInfo("c", 3);
    assertEquals("c", someInfo3.str());
    assertEquals(3, someInfo3.myInfo());

    Mutability mutability = Mutability.create("providertest");
    Environment env =
        Environment.builder(mutability)
            .setSemantics(BuckStarlark.BUCK_STARLARK_SEMANTICS)
            .setGlobals(
                Environment.GlobalFrame.createForBuiltins(
                    ImmutableMap.of(SomeInfo.PROVIDER.getName(), SomeInfo.PROVIDER)))
            .build();

    FuncallExpression ast =
        new FuncallExpression(
            new Identifier("SomeInfo"),
            ImmutableList.of(
                new Argument.Keyword(new Identifier("str"), new StringLiteral("d")),
                new Argument.Keyword(new Identifier("my_info"), new IntegerLiteral(4))));

    Object o = ast.eval(env);
    assertThat(o, Matchers.instanceOf(SomeInfo.class));
    SomeInfo someInfo4 = (SomeInfo) o;
    assertEquals("d", someInfo4.str());
    assertEquals(4, someInfo4.myInfo());
  }

  @Test
  public void infoWithSetCanBeCreatedProperly()
      throws IllegalAccessException, InstantiationException, InvocationTargetException {
    InfoWithSet someInfo1 = new ImmutableInfoWithSet(ImmutableSet.of("a"));
    assertEquals(ImmutableSet.of("a"), someInfo1.set());

    InfoWithSet someInfo2 = someInfo1.getProvider().createInfo(ImmutableSet.of("b"));
    assertEquals(ImmutableSet.of("b"), someInfo2.set());

    InfoWithSet someInfo3 = InfoWithSet.PROVIDER.createInfo(ImmutableSet.of());
    assertEquals(ImmutableSet.of(), someInfo3.set());
  }

  @Test
  public void infoWithMapCanBeCreatedProperly()
      throws IllegalAccessException, InstantiationException, InvocationTargetException {
    InfoWithMap someInfo1 = new ImmutableInfoWithMap(SkylarkDict.of(null, "a", 1));
    assertEquals(SkylarkDict.of(null, "a", 1), someInfo1.map());

    InfoWithMap someInfo2 = someInfo1.getProvider().createInfo(SkylarkDict.of(null, "b", 2));
    assertEquals(SkylarkDict.of(null, "b", 2), someInfo2.map());

    InfoWithMap someInfo3 = InfoWithMap.PROVIDER.createInfo(SkylarkDict.of(null));
    assertEquals(SkylarkDict.of(null), someInfo3.map());
  }

  @Test
  public void differentInfoInstanceProviderKeyEquals()
      throws IllegalAccessException, InstantiationException, InvocationTargetException {
    SomeInfo someInfo1 = new ImmutableSomeInfo("a", 1);

    SomeInfo someInfo2 = SomeInfo.PROVIDER.createInfo("b", 2);

    assertEquals(SomeInfo.PROVIDER.getKey(), someInfo1.getProvider().getKey());
    assertEquals(someInfo1.getProvider().getKey(), someInfo2.getProvider().getKey());
  }

  @Test
  public void differentInfoTypeProviderKeyNotEquals() {
    assertNotEquals(SomeInfo.PROVIDER.getKey(), OtherInfo.PROVIDER.getKey());
  }

  @Test
  public void defaultValuesWorkInStarlarkContext() throws InterruptedException, EvalException {

    Mutability mutability = Mutability.create("test");
    Environment env =
        Environment.builder(mutability)
            .setSemantics(BuckStarlark.BUCK_STARLARK_SEMANTICS)
            .setGlobals(
                Environment.GlobalFrame.createForBuiltins(
                    ImmutableMap.of(
                        ImmutableSomeInfo.PROVIDER.getName(), ImmutableSomeInfo.PROVIDER)))
            .build();

    FuncallExpression ast =
        new FuncallExpression(
            new Identifier(ImmutableSomeInfo.PROVIDER.getName()),
            ImmutableList.of(
                new Argument.Keyword(new Identifier("my_info"), new IntegerLiteral(2))));

    assertEquals(new ImmutableSomeInfo("default value", 2), ast.eval(env));
  }

  @Test
  public void infoWithNoDefaultValueOnAnnotationWorks() throws InterruptedException, EvalException {

    Mutability mutability = Mutability.create("test");
    Environment env =
        Environment.builder(mutability)
            .setSemantics(BuckStarlark.BUCK_STARLARK_SEMANTICS)
            .setGlobals(
                Environment.GlobalFrame.createForBuiltins(
                    ImmutableMap.of(
                        InfoWithNoDefaultValOnAnnotation.PROVIDER.getName(),
                        InfoWithNoDefaultValOnAnnotation.PROVIDER)))
            .build();

    FuncallExpression ast =
        new FuncallExpression(
            new Identifier(ImmutableInfoWithNoDefaultValOnAnnotation.PROVIDER.getName()),
            ImmutableList.of(new Argument.Keyword(new Identifier("val"), new IntegerLiteral(2))));

    assertEquals(new ImmutableInfoWithNoDefaultValOnAnnotation(2), ast.eval(env));
  }

  @Test
  public void instantiatesFromStaticMethodIfPresent() throws InterruptedException, EvalException {
    Mutability mutability = Mutability.create("providertest");
    Environment env =
        Environment.builder(mutability)
            .setSemantics(BuckStarlark.BUCK_STARLARK_SEMANTICS)
            .setGlobals(
                Environment.GlobalFrame.createForBuiltins(
                    ImmutableMap.of(
                        SomeInfoWithInstantiate.PROVIDER.getName(),
                        SomeInfoWithInstantiate.PROVIDER)))
            .build();

    FuncallExpression ast =
        new FuncallExpression(
            new Identifier("SomeInfoWithInstantiate"),
            ImmutableList.of(
                new Argument.Keyword(
                    new Identifier("str_list"),
                    new DictionaryLiteral(
                        ImmutableList.of(
                            new DictionaryLiteral.DictionaryEntryLiteral(
                                new StringLiteral("d"), new StringLiteral("d_value"))))),
                new Argument.Keyword(new Identifier("my_info"), new IntegerLiteral(4))));

    Object o = ast.eval(env);
    assertThat(o, Matchers.instanceOf(SomeInfoWithInstantiate.class));
    SomeInfoWithInstantiate someInfo4 = (SomeInfoWithInstantiate) o;
    assertEquals(ImmutableList.of("d"), someInfo4.str_list());
    assertEquals("4", someInfo4.myInfo());
  }

  @Test
  public void instantiatesFromStaticMethodWithDefaultValuesIfPresent()
      throws InterruptedException, EvalException {
    Mutability mutability = Mutability.create("providertest");
    Environment env =
        Environment.builder(mutability)
            .setSemantics(BuckStarlark.BUCK_STARLARK_SEMANTICS)
            .setGlobals(
                Environment.GlobalFrame.createForBuiltins(
                    ImmutableMap.of(
                        SomeInfoWithInstantiate.PROVIDER.getName(),
                        SomeInfoWithInstantiate.PROVIDER)))
            .build();

    FuncallExpression ast =
        new FuncallExpression(
            new Identifier("SomeInfoWithInstantiate"),
            ImmutableList.of(
                new Argument.Keyword(new Identifier("my_info"), new IntegerLiteral(4))));

    Object o = ast.eval(env);
    assertThat(o, Matchers.instanceOf(SomeInfoWithInstantiate.class));
    SomeInfoWithInstantiate someInfo4 = (SomeInfoWithInstantiate) o;
    assertEquals(ImmutableList.of("foo"), someInfo4.str_list());
    assertEquals("4", someInfo4.myInfo());
  }

  @Test
  public void validatesTypesWhenInstantiatingFromStaticMethod()
      throws InterruptedException, EvalException {
    Mutability mutability = Mutability.create("providertest");
    Environment env =
        Environment.builder(mutability)
            .setSemantics(BuckStarlark.BUCK_STARLARK_SEMANTICS)
            .setGlobals(
                Environment.GlobalFrame.createForBuiltins(
                    ImmutableMap.of(
                        SomeInfoWithInstantiate.PROVIDER.getName(),
                        SomeInfoWithInstantiate.PROVIDER)))
            .build();

    FuncallExpression ast =
        new FuncallExpression(
            new Identifier("SomeInfoWithInstantiate"),
            ImmutableList.of(
                new Argument.Keyword(
                    new Identifier("my_info"), new StringLiteral("not a number"))));

    thrown.expect(EvalException.class);
    thrown.expectMessage("expected value of type 'int'");
    ast.eval(env);
  }
}
