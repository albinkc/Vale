package dev.vale.typing.macros

import dev.vale.{Err, Interner, Keywords, Ok, RangeS, StrI, vassert, vassertSome, vimpl}
import dev.vale.highertyping.FunctionA
import dev.vale.typing.OverloadResolver.FindFunctionFailure
import dev.vale.typing.{CompileErrorExceptionT, CompilerOutputs, CouldntFindFunctionToCallT, OverloadResolver, TemplataCompiler, ast}
import dev.vale.typing.ast.{AbstractT, ArgLookupTE, BlockTE, FunctionHeaderT, FunctionDefinitionT, InterfaceFunctionCallTE, LocationInFunctionEnvironment, ParameterT, ReturnTE}
import dev.vale.typing.env.{FunctionEnvironment, TemplatasStore}
import dev.vale.typing.types.CoordT
import dev.vale.typing.ast._
import dev.vale.typing.function.FunctionCompiler.EvaluateFunctionSuccess
import dev.vale.typing.templata.{FunctionTemplata, PrototypeTemplata}

class AbstractBodyMacro(interner: Interner, keywords: Keywords, overloadResolver: OverloadResolver) extends IFunctionBodyMacro {
  val generatorId: StrI = keywords.abstractBody

  override def generateFunctionBody(
    env: FunctionEnvironment,
    coutputs: CompilerOutputs,
    generatorId: StrI,
    life: LocationInFunctionEnvironment,
    callRange: List[RangeS],
    originFunction: Option[FunctionA],
    params2: Vector[ParameterT],
    maybeRetCoord: Option[CoordT]):
  (FunctionHeaderT, ReferenceExpressionTE) = {
    val returnReferenceType2 = vassertSome(maybeRetCoord)
    vassert(params2.exists(_.virtuality == Some(AbstractT())))
    val header =
      FunctionHeaderT(
        env.fullName,
        Vector.empty,
        params2,
        returnReferenceType2,
        originFunction.map(FunctionTemplata(env.parentEnv, _)))

    // Find self, but instead of calling it like a regular function call, call it like an interface.
    // We do this instead of grabbing the prototype out of the environment because we want to get its
    // instantiation bounds too (well, we want them to be added to the coutputs).
    val prototype =
      overloadResolver.findFunction(
        env,
        coutputs,
        callRange,
        vassertSome(TemplatasStore.getImpreciseName(interner, env.fullName.localName)),
        Vector(),
        Vector(),
        params2.map(_.tyype),
        Vector(),
        true,
        true) match {
        case Ok(EvaluateFunctionSuccess(PrototypeTemplata(_, prototype), _)) => prototype
        case Err(fff @ FindFunctionFailure(_, _, _)) => throw CompileErrorExceptionT(CouldntFindFunctionToCallT(callRange, fff))
      }

    val body =
      BlockTE(
        ReturnTE(
          InterfaceFunctionCallTE(
            prototype,
            vassertSome(header.getVirtualIndex),
            prototype.returnType,
            prototype.paramTypes.zipWithIndex.map({ case (paramType, index) => ArgLookupTE(index, paramType) }))))

    (header, body)
  }
}
