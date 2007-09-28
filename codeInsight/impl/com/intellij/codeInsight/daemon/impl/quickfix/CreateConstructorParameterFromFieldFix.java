package com.intellij.codeInsight.daemon.impl.quickfix;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.daemon.QuickFixBundle;
import com.intellij.codeInsight.daemon.impl.analysis.HighlightControlFlowUtil;
import com.intellij.codeInsight.intention.IntentionAction;
import com.intellij.codeInsight.intention.impl.AssignFieldFromParameterAction;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Comparing;
import com.intellij.psi.*;
import com.intellij.psi.impl.source.jsp.jspJava.JspClass;
import com.intellij.psi.util.PsiTypesUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NotNull;

public class CreateConstructorParameterFromFieldFix implements IntentionAction {
  private final SmartPsiElementPointer myField;

  public CreateConstructorParameterFromFieldFix(@NotNull PsiField field) {
    myField = SmartPointerManager.getInstance(field.getProject()).createSmartPsiElementPointer(field);
  }

  @NotNull
  public String getText() {
    return QuickFixBundle.message("add.constructor.parameter.name");
  }

  @NotNull
  public String getFamilyName() {
    return getText();
  }

  public boolean isAvailable(@NotNull Project project, Editor editor, PsiFile file) {
    final PsiField field = getField();
    return
           field != null
           && field.getManager().isInProject(field)
           && field.getContainingClass() != null
           && !(field.getContainingClass() instanceof JspClass)
           && !field.hasModifierProperty(PsiModifier.STATIC)
      ;
  }

  public void invoke(@NotNull final Project project, final Editor editor, final PsiFile file) throws IncorrectOperationException {
    if (!CodeInsightUtil.prepareFileForWrite(file)) return;

    PsiClass aClass = getField().getContainingClass();
    PsiMethod[] constructors = aClass.getConstructors();
    if (constructors.length == 0) {
      final AddDefaultConstructorFix defaultConstructorFix = new AddDefaultConstructorFix(aClass);
      defaultConstructorFix.invoke(project, editor, file);
      aClass = getField().getContainingClass();
      constructors = aClass.getConstructors();
    }
    for (int i = 0; i < constructors.length; i++){
      if (!addParameterToConstructor(project, file, editor, getField().getContainingClass().getConstructors()[i])) break;
    }
  }

  private boolean addParameterToConstructor(final Project project, final PsiFile file, final Editor editor, PsiMethod constructor) throws IncorrectOperationException {
    PsiParameter[] parameters = constructor.getParameterList().getParameters();
    PsiExpression[] expressions = new PsiExpression[parameters.length+1];
    PsiElementFactory factory = file.getManager().getElementFactory();
    for (int i = 0; i < parameters.length; i++) {
      PsiParameter parameter = parameters[i];
      String value = PsiTypesUtil.getDefaultValueOfType(parameter.getType());
      expressions[i] = factory.createExpressionFromText(value, parameter);
    }
    expressions[parameters.length] = factory.createExpressionFromText(getField().getName(), constructor);
    final SmartPointerManager manager = SmartPointerManager.getInstance(getField().getProject());
    final SmartPsiElementPointer constructorPointer = manager.createSmartPsiElementPointer(constructor);

    ChangeMethodSignatureFromUsageFix addParamFix = new ChangeMethodSignatureFromUsageFix(constructor, expressions, PsiSubstitutor.EMPTY, constructor, true, 1);
    addParamFix.invoke(project, editor, file);
    constructor = (PsiMethod)constructorPointer.getElement();
    assert constructor != null;
    PsiParameter[] newParameters = constructor.getParameterList().getParameters();
    if (newParameters == parameters) return false; //user must have canceled dialog
    String newName = addParamFix.getNewParameterNameByOldIndex(-1);
    PsiParameter parameter = null;
    for (PsiParameter newParameter : newParameters) {
      if (Comparing.strEqual(newName, newParameter.getName())) {
        parameter = newParameter;
        break;
      }
    }
    if (parameter == null) return false;

    // do not introduce assignment in chanined constructor
    if (HighlightControlFlowUtil.getChainedConstructors(constructor) == null) {
      AssignFieldFromParameterAction.addFieldAssignmentStatement(project, getField(), parameter, editor);
    }
    return true;
  }

  private PsiField getField() {
    return (PsiField)myField.getElement();
  }

  public boolean startInWriteAction() {
    return true;
  }
}
