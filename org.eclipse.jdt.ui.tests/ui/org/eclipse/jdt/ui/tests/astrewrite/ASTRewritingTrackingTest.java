/*******************************************************************************
 * Copyright (c) 2000, 2003 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials 
 * are made available under the terms of the Common Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/cpl-v10.html
 * 
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.jdt.ui.tests.astrewrite;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;

import junit.framework.Test;
import junit.framework.TestSuite;

import org.eclipse.jdt.testplugin.JavaProjectHelper;

import org.eclipse.jdt.core.ICompilationUnit;
import org.eclipse.jdt.core.IJavaProject;
import org.eclipse.jdt.core.IPackageFragment;
import org.eclipse.jdt.core.IPackageFragmentRoot;
import org.eclipse.jdt.core.JavaCore;
import org.eclipse.jdt.core.formatter.DefaultCodeFormatterConstants;

import org.eclipse.jdt.core.dom.*;

import org.eclipse.jdt.ui.tests.core.ProjectTestSetup;

import org.eclipse.jdt.internal.corext.dom.ITrackedNodePosition;
import org.eclipse.jdt.internal.corext.dom.NewASTRewrite;

public class ASTRewritingTrackingTest extends ASTRewritingTest {

	private static final Class THIS= ASTRewritingTrackingTest.class;
	
	private IJavaProject fJProject1;
	private IPackageFragmentRoot fSourceFolder;
	
	

	public ASTRewritingTrackingTest(String name) {
		super(name);
	}
	
	public static Test allTests() {
		return new ProjectTestSetup(new TestSuite(THIS));
	}
	
	public static Test suite() {
		if (true) {
			return allTests();
		} else {
			TestSuite suite= new TestSuite();
			suite.addTest(new ASTRewritingTrackingTest("testNamesWithPlaceholder"));
			return new ProjectTestSetup(suite);
		}
	}
	
	protected void setUp() throws Exception {
		Hashtable options= JavaCore.getDefaultOptions();
		options.put(DefaultCodeFormatterConstants.FORMATTER_TAB_CHAR, JavaCore.SPACE);
		options.put(DefaultCodeFormatterConstants.FORMATTER_TAB_SIZE, "4");
		JavaCore.setOptions(options);			
		
		fJProject1= ProjectTestSetup.getProject();
		
		fSourceFolder= JavaProjectHelper.addSourceContainer(fJProject1, "src");
	}
	
	protected void tearDown() throws Exception {
		JavaProjectHelper.clear(fJProject1, ProjectTestSetup.getDefaultClasspath());
	}
		
	
	public void testNamesWithDelete() throws Exception {
		
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class C {\n");
		buf.append("\n");
		buf.append("    public int x1;\n");
		buf.append("\n");
		buf.append("    public void foo(String s, int i) {\n");
		buf.append("        while (i == 0) {\n");
		buf.append("            i--;\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");		
		ICompilationUnit cu= pack1.createCompilationUnit("C.java", buf.toString(), false, null);
	
		CompilationUnit astRoot= createAST(cu);
		AST ast= astRoot.getAST();
		NewASTRewrite rewrite= new NewASTRewrite(ast);
		
		ArrayList names= new ArrayList();
		ArrayList positions= new ArrayList();
		
		TypeDeclaration typeC= findTypeDeclaration(astRoot, "C");
		ITrackedNodePosition position= rewrite.markAsTracked(typeC.getName());
		names.add("C");
		positions.add(position);
		
		List decls= typeC.bodyDeclarations();
		
		MethodDeclaration method= (MethodDeclaration) decls.get(1);
		ITrackedNodePosition position2= rewrite.markAsTracked(method.getName());
		names.add("foo");
		positions.add(position2);
		
		FieldDeclaration field= (FieldDeclaration) decls.get(0);
		rewrite.markAsRemoved(field, null);
						
		String preview= evaluateRewrite(cu, rewrite);
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class C {\n");
		buf.append("\n");
		buf.append("    public void foo(String s, int i) {\n");
		buf.append("        while (i == 0) {\n");
		buf.append("            i--;\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");	
		String expected= buf.toString();
		assertEqualString(preview, expected);
		
		assertCorrectTracking(names, positions, expected);

	}
	
	private void assertCorrectTracking(List names, List positions, String expected) {
		for (int i= 0; i < names.size(); i++) {
			String name= (String) names.get(i);
			ITrackedNodePosition pos= (ITrackedNodePosition) positions.get(i);
			String string= expected.substring(pos.getStartPosition(), pos.getStartPosition() + pos.getLength());
			assertEqualString(string, name);
		}
	}

	public void testNamesWithInsert() throws Exception {
		
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class C {\n");
		buf.append("\n");
		buf.append("    public int x1;\n");
		buf.append("\n");
		buf.append("    public void foo(String s, int i) {\n");
		buf.append("        while (i == 0) {\n");
		buf.append("            i--;\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");	
		ICompilationUnit cu= pack1.createCompilationUnit("C.java", buf.toString(), false, null);
	
		CompilationUnit astRoot= createAST(cu);
		AST ast= astRoot.getAST();
		
		NewASTRewrite rewrite= new NewASTRewrite(ast);
		
		ArrayList names= new ArrayList();
		ArrayList positions= new ArrayList();
		
		TypeDeclaration typeC= findTypeDeclaration(astRoot, "C");
		ITrackedNodePosition position= rewrite.markAsTracked(typeC.getName());
		names.add("C");
		positions.add(position);
		
		List decls= typeC.bodyDeclarations();
		
		MethodDeclaration method= (MethodDeclaration) decls.get(1);
		position= rewrite.markAsTracked(method.getName());
		names.add("foo");
		positions.add(position);
		
		FieldDeclaration field= (FieldDeclaration) decls.get(0);
		List fragments= field.fragments();
		VariableDeclarationFragment frag1= (VariableDeclarationFragment) fragments.get(0);
		position= rewrite.markAsTracked(frag1.getName());
		names.add("x1");
		positions.add(position);
		
		VariableDeclarationFragment newFrag= ast.newVariableDeclarationFragment();
		newFrag.setName(ast.newSimpleName("newVariable"));
		newFrag.setExtraDimensions(2);

		rewrite.getListRewrite(field, FieldDeclaration.FRAGMENTS_PROPERTY).insertFirst(newFrag, null);

						
		String preview= evaluateRewrite(cu, rewrite);
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class C {\n");
		buf.append("\n");
		buf.append("    public int newVariable[][], x1;\n");
		buf.append("\n");
		buf.append("    public void foo(String s, int i) {\n");
		buf.append("        while (i == 0) {\n");
		buf.append("            i--;\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");	
		String expected= buf.toString();
		assertEqualString(preview, expected);

		assertCorrectTracking(names, positions, expected);

	}
	
	public void testNamesWithReplace() throws Exception {
		
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class C {\n");
		buf.append("\n");
		buf.append("    public int x1;\n");
		buf.append("\n");
		buf.append("    public void foo(String s, int i) {\n");
		buf.append("        while (i == 0) {\n");
		buf.append("            ++i;\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");	
		ICompilationUnit cu= pack1.createCompilationUnit("C.java", buf.toString(), false, null);
	
		CompilationUnit astRoot= createAST(cu);
		AST ast= astRoot.getAST();
		
		NewASTRewrite rewrite= new NewASTRewrite(ast);
		
		ArrayList names= new ArrayList();
		ArrayList positions= new ArrayList();
		
		// change type name
		TypeDeclaration typeC= findTypeDeclaration(astRoot, "C");
		SimpleName newName= ast.newSimpleName("XX");
		rewrite.markAsReplaced(typeC.getName(), newName, null);
		ITrackedNodePosition position= rewrite.markAsTracked(newName);
		names.add("XX");
		positions.add(position);
		
		List decls= typeC.bodyDeclarations();
		
		MethodDeclaration method= (MethodDeclaration) decls.get(1);
		position= rewrite.markAsTracked(method.getName());
		names.add("foo");
		positions.add(position);
		
		WhileStatement whileStatement= (WhileStatement) method.getBody().statements().get(0);
		PrefixExpression prefixExpression= (PrefixExpression) ((ExpressionStatement) ((Block) whileStatement.getBody()).statements().get(0)).getExpression();
		position= rewrite.markAsTracked(prefixExpression.getOperand());
		names.add("i");
		positions.add(position);
				
		FieldDeclaration field= (FieldDeclaration) decls.get(0);
		List fragments= field.fragments();
		VariableDeclarationFragment frag1= (VariableDeclarationFragment) fragments.get(0);
		position= rewrite.markAsTracked(frag1.getName());
		names.add("x1");
		positions.add(position);
		
		// change modifier
		int newModifiers= Modifier.STATIC | Modifier.TRANSIENT | Modifier.PRIVATE;
		rewrite.set(field, FieldDeclaration.MODIFIERS_PROPERTY, new Integer(newModifiers), null);
								
		String preview= evaluateRewrite(cu, rewrite);
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class XX {\n");
		buf.append("\n");
		buf.append("    private static transient int x1;\n");
		buf.append("\n");
		buf.append("    public void foo(String s, int i) {\n");
		buf.append("        while (i == 0) {\n");
		buf.append("            ++i;\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");	
		String expected= buf.toString();
		assertEqualString(preview, expected);
		
		assertCorrectTracking(names, positions, expected);
	}
	
	public void testNamesWithMove1() throws Exception {
		
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class C {\n");
		buf.append("\n");
		buf.append("    public int x1;\n");
		buf.append("\n");
		buf.append("    public void foo(String s, int i) {\n");
		buf.append("        while (i == 0) {\n");
		buf.append("            ++i;\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");	
		ICompilationUnit cu= pack1.createCompilationUnit("C.java", buf.toString(), false, null);
	
		CompilationUnit astRoot= createAST(cu);
		AST ast= astRoot.getAST();
		NewASTRewrite rewrite= new NewASTRewrite(ast);
		
		ArrayList names= new ArrayList();
		ArrayList positions= new ArrayList();
		
		// change type name
		TypeDeclaration typeC= findTypeDeclaration(astRoot, "C");
		ITrackedNodePosition position= rewrite.markAsTracked(typeC.getName());
		names.add("C");
		positions.add(position);
		
		List decls= typeC.bodyDeclarations();
		
		MethodDeclaration method= (MethodDeclaration) decls.get(1);
		position= rewrite.markAsTracked(method.getName());
		names.add("foo");
		positions.add(position);
		
		WhileStatement whileStatement= (WhileStatement) method.getBody().statements().get(0);
		PrefixExpression prefixExpression= (PrefixExpression) ((ExpressionStatement) ((Block) whileStatement.getBody()).statements().get(0)).getExpression();
		position= rewrite.markAsTracked(prefixExpression.getOperand());
		names.add("i");
		positions.add(position);
					
		FieldDeclaration field= (FieldDeclaration) decls.get(0);
		List fragments= field.fragments();
		VariableDeclarationFragment frag1= (VariableDeclarationFragment) fragments.get(0);
		position= rewrite.markAsTracked(frag1.getName());
		names.add("x1");
		positions.add(position);

		// move method before field
		ASTNode placeHolder= rewrite.createMoveTarget(method);
		rewrite.getListRewrite(typeC, TypeDeclaration.BODY_DECLARATIONS_PROPERTY).insertFirst(placeHolder, null);
								
		String preview= evaluateRewrite(cu, rewrite);
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class C {\n");
		buf.append("\n");
		buf.append("    public void foo(String s, int i) {\n");
		buf.append("        while (i == 0) {\n");
		buf.append("            ++i;\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    public int x1;\n");
		buf.append("}\n");	
		String expected= buf.toString();
		assertEqualString(preview, expected);
		
		assertCorrectTracking(names, positions, expected);

	}
	
	public void testNamesWithMove2() throws Exception {
		
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class C {\n");
		buf.append("    public void foo(String s, int i) {\n");
		buf.append("        while (i == 0) {\n");
		buf.append("            ++i;\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");	
		ICompilationUnit cu= pack1.createCompilationUnit("C.java", buf.toString(), false, null);
	
		CompilationUnit astRoot= createAST(cu);
		AST ast= astRoot.getAST();
		NewASTRewrite rewrite= new NewASTRewrite(ast);
		
		ArrayList names= new ArrayList();
		ArrayList positions= new ArrayList();
		
		// change type name
		TypeDeclaration typeC= findTypeDeclaration(astRoot, "C");
		ITrackedNodePosition position= rewrite.markAsTracked(typeC.getName());
		names.add("C");
		positions.add(position);
		
		List decls= typeC.bodyDeclarations();
		
		MethodDeclaration method= (MethodDeclaration) decls.get(0);
		position= rewrite.markAsTracked(method.getName());
		names.add("foo");
		positions.add(position);
		
		WhileStatement whileStatement= (WhileStatement) method.getBody().statements().get(0);
		PrefixExpression prefixExpression= (PrefixExpression) ((ExpressionStatement) ((Block) whileStatement.getBody()).statements().get(0)).getExpression();
		position= rewrite.markAsTracked(prefixExpression.getOperand());
		names.add("i");
		positions.add(position);

		// move method before field
		ASTNode placeHolder= rewrite.createMoveTarget(whileStatement);
		
		TryStatement tryStatement= ast.newTryStatement();
		tryStatement.getBody().statements().add(placeHolder);
		tryStatement.setFinally(ast.newBlock());
		rewrite.markAsReplaced(whileStatement, tryStatement, null);
								
		String preview= evaluateRewrite(cu, rewrite);
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class C {\n");
		buf.append("    public void foo(String s, int i) {\n");
		buf.append("        try {\n");
		buf.append("            while (i == 0) {\n");
		buf.append("                ++i;\n");
		buf.append("            }\n");
		buf.append("        } finally {\n");
		buf.append("        }\n");
		buf.append("    }\n");
		buf.append("}\n");	
		String expected= buf.toString();
		assertEqualString(preview, expected);
		
		assertCorrectTracking(names, positions, expected);
	}	
	
	public void testNamesWithMove3() throws Exception {
		
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class C {\n");
		buf.append("\n");
		buf.append("    public int x1;\n");
		buf.append("\n");
		buf.append("    public void foo(String s, int i) {\n");
		buf.append("    }\n");
		buf.append("}\n");	
		ICompilationUnit cu= pack1.createCompilationUnit("C.java", buf.toString(), false, null);
	
		CompilationUnit astRoot= createAST(cu);
		AST ast= astRoot.getAST();
		NewASTRewrite rewrite= new NewASTRewrite(ast);
		
		ArrayList names= new ArrayList();
		ArrayList positions= new ArrayList();
		
		// change type name
		TypeDeclaration typeC= findTypeDeclaration(astRoot, "C");
		ITrackedNodePosition position= rewrite.markAsTracked(typeC.getName());
		names.add("C");
		positions.add(position);
		
		List decls= typeC.bodyDeclarations();
		
		MethodDeclaration method= (MethodDeclaration) decls.get(1);
		position=  rewrite.markAsTracked(method.getName());
		names.add("foo");
		positions.add(position);
		
		// move method before field
		ASTNode placeHolder= rewrite.createMoveTarget(method);
		
		rewrite.getListRewrite(typeC, TypeDeclaration.BODY_DECLARATIONS_PROPERTY).insertFirst(placeHolder, null);
								
		String preview= evaluateRewrite(cu, rewrite);
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class C {\n");
		buf.append("\n");
		buf.append("    public void foo(String s, int i) {\n");
		buf.append("    }\n");
		buf.append("\n");
		buf.append("    public int x1;\n");
		buf.append("}\n");	
		String expected= buf.toString();
		assertEqualString(preview, expected);
		
		assertCorrectTracking(names, positions, expected);

	}
	public void testNamesWithPlaceholder() throws Exception {
		
		IPackageFragment pack1= fSourceFolder.createPackageFragment("test1", false, null);
		StringBuffer buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class C {\n");
		buf.append("    public String foo(Object s) {\n");
		buf.append("        return s;\n");
		buf.append("    }\n");
		buf.append("}\n");	
		ICompilationUnit cu= pack1.createCompilationUnit("C.java", buf.toString(), false, null);
	
		CompilationUnit astRoot= createAST(cu);
		AST ast= astRoot.getAST();
		NewASTRewrite rewrite= new NewASTRewrite(ast);
		
		ArrayList names= new ArrayList();
		ArrayList positions= new ArrayList();
		
		// change type name
		TypeDeclaration typeC= findTypeDeclaration(astRoot, "C");
		ITrackedNodePosition position= rewrite.markAsTracked(typeC.getName());
		names.add("C");
		positions.add(position);
		
		List decls= typeC.bodyDeclarations();
		
		MethodDeclaration method= (MethodDeclaration) decls.get(0);
		position= rewrite.markAsTracked(method.getName());
		names.add("foo");
		positions.add(position);
		
		ReturnStatement returnStatement= (ReturnStatement) method.getBody().statements().get(0);
		
		CastExpression castExpression= ast.newCastExpression();
		Type type= (Type) rewrite.createStringPlaceholder("String", NewASTRewrite.TYPE);
		Expression expression= (Expression) rewrite.createMoveTarget(returnStatement.getExpression());
		castExpression.setType(type);
		castExpression.setExpression(expression);
		
		rewrite.markAsReplaced(returnStatement.getExpression(), castExpression, null);
		
		position= rewrite.markAsTracked(type);
		names.add("String");
		positions.add(position);
		
		position= rewrite.markAsTracked(expression);
		names.add("s");
		positions.add(position);
		
		String preview= evaluateRewrite(cu, rewrite);
		
		buf= new StringBuffer();
		buf.append("package test1;\n");
		buf.append("public class C {\n");
		buf.append("    public String foo(Object s) {\n");
		buf.append("        return (String) s;\n");
		buf.append("    }\n");
		buf.append("}\n");	
		String expected= buf.toString();
		assertEqualString(preview, expected);
		
		assertCorrectTracking(names, positions, expected);

	}	

	
}



