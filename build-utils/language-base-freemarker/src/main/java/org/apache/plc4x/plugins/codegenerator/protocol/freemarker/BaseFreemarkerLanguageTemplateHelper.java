/*
Licensed to the Apache Software Foundation (ASF) under one
or more contributor license agreements.  See the NOTICE file
distributed with this work for additional information
regarding copyright ownership.  The ASF licenses this file
to you under the Apache License, Version 2.0 (the
"License"); you may not use this file except in compliance
with the License.  You may obtain a copy of the License at

  http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing,
software distributed under the License is distributed on an
"AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
KIND, either express or implied.  See the License for the
specific language governing permissions and limitations
under the License.
*/
package org.apache.plc4x.plugins.codegenerator.protocol.freemarker;

import net.objecthunter.exp4j.Expression;
import net.objecthunter.exp4j.ExpressionBuilder;
import org.apache.plc4x.plugins.codegenerator.types.definitions.*;
import org.apache.plc4x.plugins.codegenerator.types.fields.*;
import org.apache.plc4x.plugins.codegenerator.types.references.ComplexTypeReference;
import org.apache.plc4x.plugins.codegenerator.types.references.SimpleTypeReference;
import org.apache.plc4x.plugins.codegenerator.types.references.TypeReference;
import org.apache.plc4x.plugins.codegenerator.types.terms.*;

import java.util.*;
import java.util.stream.Collectors;

public abstract class BaseFreemarkerLanguageTemplateHelper implements FreemarkerLanguageTemplateHelper {

    private final TypeDefinition thisType;
    private final String protocolName;
    private final String flavorName;
    private final Map<String, TypeDefinition> types;

    // In mspec we are using some virtual virtual fields that are useful for code generation.
    // As they should be shared over all language template implementations,
    // these are defined here manually.
    private static Map<String, SimpleTypeReference> builtInFields;
    {
        builtInFields = new HashMap<>();
        builtInFields.put("curPos", new SimpleTypeReference() {
            @Override
            public SimpleBaseType getBaseType() {
                return SimpleBaseType.UINT;
            }

            @Override
            public int getSizeInBits() {
                return 16;
            }
        });
        builtInFields.put("startPos", new SimpleTypeReference() {
            @Override
            public SimpleBaseType getBaseType() {
                return SimpleBaseType.UINT;
            }

            @Override
            public int getSizeInBits() {
                return 16;
            }
        });
    }

    public BaseFreemarkerLanguageTemplateHelper(TypeDefinition thisType, String protocolName, String flavorName, Map<String, TypeDefinition> types) {
        this.thisType = thisType;
        this.protocolName = protocolName;
        this.flavorName = flavorName;
        this.types = types;
    }

    protected TypeDefinition getThisTypeDefinition() {
        return thisType;
    }

    protected String getProtocolName() {
        return protocolName;
    }

    protected String getFlavorName() {
        return flavorName;
    }

    protected Map<String, TypeDefinition> getTypeDefinitions() {
        return types;
    }

    protected static Map<String, SimpleTypeReference> getBuiltInFieldTypes() {
        return builtInFields;
    }

    /* *********************************************************************************
     * Methods that are language-dependent.
     **********************************************************************************/

    public abstract String getLanguageTypeNameForField(Field field);

    public abstract String getLanguageTypeNameForTypeReference(TypeReference typeReference);

    public abstract String getReadBufferReadMethodCall(SimpleTypeReference simpleTypeReference);

    public abstract String getWriteBufferReadMethodCall(SimpleTypeReference simpleTypeReference);

    public abstract String getNullValueForTypeReference(TypeReference typeReference);

    public abstract String getDiscriminatorName(Term discriminatorExpression);

    /* *********************************************************************************
     * Methods related to type-references.
     **********************************************************************************/

    /**
     * @param typeReference type reference
     * @return true if the given type reference is a simple type reference.
     */
    public boolean isSimpleTypeReference(TypeReference typeReference) {
        return typeReference instanceof SimpleTypeReference;
    }

    /**
     * @param typeReference type reference
     * @return true if the given type reference is a complex type reference.
     */
    public boolean isComplexTypeReference(TypeReference typeReference) {
        return typeReference instanceof ComplexTypeReference;
    }

    /**
     * Helper for collecting referenced complex types as these usually ned to be
     * imported in some way.
     *
     * @return Collection of all complex type references used in fields or enum constants.
     */
    public Collection<ComplexTypeReference> getComplexTypeReferences() {
        return getComplexTypeReferences(thisType);
    }

    /**
     * Helper for collecting referenced complex types as these usually need to be
     * imported in some way.
     *
     * @param baseType the base type we want to get the type references from
     * @return collection of complex type references used in the type.
     */
    public Collection<ComplexTypeReference> getComplexTypeReferences(TypeDefinition baseType) {
        Set<ComplexTypeReference> complexTypeReferences = new HashSet<>();
        // If this is a subtype of a discriminated type, we have to add a reference to the parent type.
        if (baseType instanceof DiscriminatedComplexTypeDefinition) {
            DiscriminatedComplexTypeDefinition discriminatedComplexTypeDefinition = (DiscriminatedComplexTypeDefinition) baseType;
            if(!discriminatedComplexTypeDefinition.isAbstract()) {
                complexTypeReferences.add((ComplexTypeReference)
                    discriminatedComplexTypeDefinition.getParentType().getTypeReference());
            }
        }
        // If it's a complex type definition, add all the types referenced by any property fields
        // (Includes any types referenced by sub-types in case this is a discriminated type parent)
        if (baseType instanceof ComplexTypeDefinition) {
            ComplexTypeDefinition complexTypeDefinition = (ComplexTypeDefinition) baseType;
            for (Field field : complexTypeDefinition.getFields()) {
                if(field instanceof PropertyField) {
                    PropertyField propertyField = (PropertyField) field;
                    if (propertyField.getType() instanceof ComplexTypeReference) {
                        ComplexTypeReference complexTypeReference = (ComplexTypeReference) propertyField.getType();
                        complexTypeReferences.add(complexTypeReference);
                    }
                } else if(field instanceof SwitchField) {
                    SwitchField switchField = (SwitchField) field;
                    for (DiscriminatedComplexTypeDefinition switchCase : switchField.getCases()) {
                        complexTypeReferences.addAll(getComplexTypeReferences(switchCase));
                    }
                }
            }
        }
        // In case this is a enum type, we have to check all the constant types.
        else if (baseType instanceof EnumTypeDefinition) {
            for (String constantName : ((EnumTypeDefinition) baseType).getConstantNames()) {
                final TypeReference constantType = ((EnumTypeDefinition) thisType).getConstantType(constantName);
                if (constantType instanceof ComplexTypeReference) {
                    ComplexTypeReference complexTypeReference = (ComplexTypeReference) constantType;
                    complexTypeReferences.add(complexTypeReference);
                }
            }
        }
        // If the type has any parser arguments, these have to be checked too.
        if(baseType.getParserArguments() != null) {
            for (Argument parserArgument : baseType.getParserArguments()) {
                if (parserArgument.getType() instanceof ComplexTypeReference) {
                    ComplexTypeReference complexTypeReference = (ComplexTypeReference) parserArgument.getType();
                    complexTypeReferences.add(complexTypeReference);
                }
            }
        }
        return complexTypeReferences;
    }

    /**
     * Little helper to return the type of a given property.
     *
     * @param baseType base type definition that contains the given property.
     * @param propertyName name of the property
     * @return the type reference of the given property
     */
    public Optional<TypeReference> getTypeReferenceForProperty(ComplexTypeDefinition baseType, String propertyName) {
        // If this is a built-in type, use that.
        if(builtInFields.containsKey(propertyName)) {
            return Optional.of(builtInFields.get(propertyName));
        }
        // Check if the expression root is referencing a field
        final Optional<PropertyField> propertyFieldOptional = baseType.getFields().stream().filter(
            field -> field instanceof PropertyField).map(field -> (PropertyField) field).filter(
            propertyField -> propertyField.getName().equals(propertyName)).findFirst();
        if(propertyFieldOptional.isPresent()) {
            final PropertyField propertyField = propertyFieldOptional.get();
            return Optional.of(propertyField.getType());
        }
        // Check if the expression is a ImplicitField
        final Optional<ImplicitField> implicitFieldOptional = baseType.getFields().stream().filter(
            field -> field instanceof ImplicitField).map(field -> (ImplicitField) field).filter(
            implicitField -> implicitField.getName().equals(propertyName)).findFirst();
        if(implicitFieldOptional.isPresent()) {
            final ImplicitField implicitField = implicitFieldOptional.get();
            return Optional.of(implicitField.getType());
        }
        // Check if the expression root is referencing an argument
        if(baseType.getParserArguments() != null) {
            final Optional<Argument> argumentOptional = Arrays.stream(baseType.getParserArguments()).filter(
                argument -> argument.getName().equals(propertyName)).findFirst();
            if (argumentOptional.isPresent()) {
                final Argument argument = argumentOptional.get();
                return Optional.of(argument.getType());
            }
        }
        // Check if the expression is a DiscriminatorField
        // This is a more theoretical case where the expression is referencing a discriminator value of the current type
        final Optional<DiscriminatorField> discriminatorFieldOptional = baseType.getFields().stream().filter(
            field -> field instanceof DiscriminatorField).map(field -> (DiscriminatorField) field).filter(
            discriminatorField -> discriminatorField.getName().equals(propertyName)).findFirst();
        if(discriminatorFieldOptional.isPresent()) {
            final DiscriminatorField discriminatorField = discriminatorFieldOptional.get();
            return Optional.of(discriminatorField.getType());
        }
        return Optional.empty();
    }

    /**
     * Enums are always based on a main type. This helper accesses this information in a safe manner.
     *
     * @param typeReference type reference
     * @return simple type reference for the enum type referenced by the given type reference
     */
    public SimpleTypeReference getEnumBaseTypeReference(TypeReference typeReference) {
        if (!(typeReference instanceof ComplexTypeReference)) {
            throw new RuntimeException("type reference for enum types must be of type complex type");
        }
        ComplexTypeReference complexTypeReference = (ComplexTypeReference) typeReference;
        final TypeDefinition typeDefinition = types.get(complexTypeReference.getName());
        if(typeDefinition == null) {
            throw new RuntimeException("Couldn't find given enum type definition with name " + complexTypeReference.getName());
        }
        if(!(typeDefinition instanceof EnumTypeDefinition)) {
            throw new RuntimeException("Referenced tpye with name " + complexTypeReference.getName() + " is not an enum type");
        }
        EnumTypeDefinition enumTypeDefinition = (EnumTypeDefinition) typeDefinition;
        // Enum types always have simple type references.
        return (SimpleTypeReference) enumTypeDefinition.getType();
    }

    /* *********************************************************************************
     * Methods related to fields.
     **********************************************************************************/

    public boolean isSwitchField(Field field) {
        return field instanceof SwitchField;
    }

    public boolean isEnumField(Field field) {
        return field instanceof EnumField;
    }

    public boolean isCountArrayField(Field field) {
        if(field instanceof ArrayField) {
            ArrayField arrayField = (ArrayField) field;
            return arrayField.getLoopType() == ArrayField.LoopType.COUNT;
        }
        return false;
    }

    public boolean isLengthArrayField(Field field) {
        if(field instanceof ArrayField) {
            ArrayField arrayField = (ArrayField) field;
            return arrayField.getLoopType() == ArrayField.LoopType.LENGTH;
        }
        return false;
    }

    public boolean isTerminatedArrayField(Field field) {
        if(field instanceof ArrayField) {
            ArrayField arrayField = (ArrayField) field;
            return arrayField.getLoopType() == ArrayField.LoopType.TERMINATED;
        }
        return false;
    }

    /**
     * @return switch field of the current base type.
     */
    protected SwitchField getSwitchField() {
        return getSwitchField(thisType);
    }

    /**
     * @return switch field of the provided base type.
     */
    protected SwitchField getSwitchField(TypeDefinition typeDefinition) {
        if (typeDefinition instanceof ComplexTypeDefinition) {
            ComplexTypeDefinition complexTypeDefinition = (ComplexTypeDefinition) typeDefinition;
            // Sebastian would be proud of me ;-)
            return (SwitchField) complexTypeDefinition.getFields().stream().filter(
                field -> field instanceof SwitchField).findFirst().orElse(null);
        }
        return null;
    }

    public Collection<Field> getPropertyAndSwitchFields() {
        return getPropertyAndSwitchFields(thisType);
    }

    public Collection<Field> getPropertyAndSwitchFields(TypeDefinition typeDefinition) {
        if(thisType instanceof ComplexTypeDefinition) {
            return ((ComplexTypeDefinition) thisType).getFields().stream().filter(
                field -> (field instanceof PropertyField) || (field instanceof SwitchField)).collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    /* *********************************************************************************
     * Methods related to type-definitions.
     **********************************************************************************/

    public boolean isDiscriminatedParentTypeDefinition(TypeDefinition typeDefinition) {
        return (typeDefinition instanceof ComplexTypeDefinition) && ((ComplexTypeDefinition) typeDefinition).isAbstract();
    }

    public boolean isDiscriminatedChildTypeDefinition(TypeDefinition typeDefinition) {
        return (typeDefinition instanceof DiscriminatedComplexTypeDefinition) && !((ComplexTypeDefinition) typeDefinition).isAbstract();
    }

    public TypeDefinition getTypeDefinitionForTypeReference(TypeReference typeReference) {
        if(!(typeReference instanceof ComplexTypeReference)) {
            throw new RuntimeException("Type reference must be a complex type reference");
        }
        return getTypeDefinitions().get(((ComplexTypeReference) typeReference).getName());
    }

    /**
     * @return list of sub-types for the current base type or an empty collection, if there are none
     */
    public List<DiscriminatedComplexTypeDefinition> getSubTypeDefinitions() {
        return getSubTypeDefinitions(thisType);
    }

    /**
     * @return list of sub-types for a given type definition or an empty collection, if there are none
     */
    public List<DiscriminatedComplexTypeDefinition> getSubTypeDefinitions(TypeDefinition type) {
        SwitchField switchField = getSwitchField(type);
        if (switchField != null) {
            return switchField.getCases();
        }
        return Collections.emptyList();
    }

    /* *********************************************************************************
     * Methods related to terms and expressions.
     **********************************************************************************/

    /**
     * Check if the expression doesn't reference any variables.
     * If this is the case, the expression can be evaluated at code-generation time.
     *
     * @param term term
     * @return true if it doesn't reference any variable literals.
     */
    protected boolean isFixedValueExpression(Term term) {
        if (term instanceof VariableLiteral) {
            return false;
        }
        if (term instanceof UnaryTerm) {
            UnaryTerm unaryTerm = (UnaryTerm) term;
            return isFixedValueExpression(unaryTerm.getA());
        }
        if (term instanceof BinaryTerm) {
            BinaryTerm binaryTerm = (BinaryTerm) term;
            return isFixedValueExpression(binaryTerm.getA()) && isFixedValueExpression(binaryTerm.getB());
        }
        if (term instanceof TernaryTerm) {
            TernaryTerm ternaryTerm = (TernaryTerm) term;
            return isFixedValueExpression(ternaryTerm.getA()) && isFixedValueExpression(ternaryTerm.getB()) &&
                isFixedValueExpression(ternaryTerm.getC());
        }
        return true;
    }

    protected int evaluateFixedValueExpression(Term term) {
        final Expression expression = new ExpressionBuilder(toString(term)).build();
        return (int) expression.evaluate();
    }

    protected String toString(Term term) {
        if (term instanceof NullLiteral) {
            return "null";
        }
        if (term instanceof BooleanLiteral) {
            return Boolean.toString(((BooleanLiteral) term).getValue());
        }
        if (term instanceof NumericLiteral) {
            return ((NumericLiteral) term).getNumber().toString();
        }
        if (term instanceof StringLiteral) {
            return "\"" + ((StringLiteral) term).getValue() + "\"";
        }
        if (term instanceof UnaryTerm) {
            return ((UnaryTerm) term).getOperation() + toString(((UnaryTerm) term).getA());
        }
        if (term instanceof BinaryTerm) {
            return toString(((BinaryTerm) term).getA()) + ((BinaryTerm) term).getOperation() + toString(((BinaryTerm) term).getB());
        }
        if (term instanceof TernaryTerm) {
            return "(" + toString(((TernaryTerm) term).getA()) + ") ? (" + toString(((TernaryTerm) term).getB()) +
                ") : (" + toString(((TernaryTerm) term).getC()) + ")";
        }
        return "";
    }

    /* *********************************************************************************
     * Methods related to discriminators.
     **********************************************************************************/

    private Optional<TypeReference> getDiscriminatorType(ComplexTypeDefinition parentType, Term disciminatorExpression) {
        if (!(disciminatorExpression instanceof VariableLiteral)) {
            throw new RuntimeException("Currently no arithmetic expressions are supported as discriminator expressions.");
        }
        VariableLiteral variableLiteral = (VariableLiteral) disciminatorExpression;
        Optional<TypeReference> type = getTypeReferenceForProperty(parentType, variableLiteral.getName());
        // If we found something but there's a "rest" left, we got to use the type we
        // found in this level, get that type's definition and continue from there.
        if (type.isPresent() && (variableLiteral.getChild() != null)) {
            TypeReference typeReference = type.get();
            if (typeReference instanceof ComplexTypeReference) {
                ComplexTypeReference complexTypeReference = (ComplexTypeReference) typeReference;
                final TypeDefinition typeDefinition = this.types.get(complexTypeReference.getName());
                if (typeDefinition instanceof ComplexTypeDefinition) {
                    return getDiscriminatorType((ComplexTypeDefinition) typeDefinition, variableLiteral.getChild());
                }
            }
        }
        return type;
    }

    public Map<String, TypeReference> getDiscriminatorTypes() {
        // Get the parent type (Which contains the typeSwitch field)
        ComplexTypeDefinition parentType;
        if (thisType instanceof DiscriminatedComplexTypeDefinition) {
            parentType = (ComplexTypeDefinition) thisType.getParentType();
        } else {
            parentType = (ComplexTypeDefinition) thisType;
        }
        // Get the typeSwitch field from that.
        final SwitchField switchField = getSwitchField(parentType);
        if (switchField != null) {
            Map<String, TypeReference> discriminatorTypes = new TreeMap<>();
            for (Term discriminatorExpression : switchField.getDiscriminatorExpressions()) {
                // Get some symbolic name we can use.
                String discriminatorName = getDiscriminatorName(discriminatorExpression);
                Optional<TypeReference> discriminatorType = getDiscriminatorType(parentType, discriminatorExpression);
                discriminatorTypes.put(discriminatorName, discriminatorType.orElse(null));
            }
            return discriminatorTypes;
        }
        return Collections.emptyMap();
    }

    public Map<String, Map<String, String>> getDiscriminatorValues() {
        // Get the parent type (Which contains the typeSwitch field)
        ComplexTypeDefinition parentType;
        if (thisType instanceof DiscriminatedComplexTypeDefinition) {
            parentType = (ComplexTypeDefinition) thisType.getParentType();
        } else {
            parentType = (ComplexTypeDefinition) thisType;
        }
        // Get the typeSwitch field from that.
        final SwitchField switchField = getSwitchField(parentType);
        if (switchField != null) {
            // Get the symbolic names of all discriminators
            String[] discriminatorNames = new String[switchField.getDiscriminatorExpressions().length];
            for (int i = 0; i < switchField.getDiscriminatorExpressions().length; i++) {
                discriminatorNames[i] = getDiscriminatorName(switchField.getDiscriminatorExpressions()[i]);
            }
            // Build a map containing the named discriminator values for every case of the typeSwitch.
            Map<String, Map<String, String>> discriminatorTypes = new TreeMap<>();
            for (DiscriminatedComplexTypeDefinition switchCase : switchField.getCases()) {
                discriminatorTypes.put(switchCase.getName(), new TreeMap<>());
                for (int i = 0; i < switchField.getDiscriminatorExpressions().length; i++) {
                    String discriminatorValue;
                    if (i < switchCase.getDiscriminatorValues().length) {
                        discriminatorValue = switchCase.getDiscriminatorValues()[i];
                    } else {
                        discriminatorValue = null;
                    }
                    discriminatorTypes.get(switchCase.getName()).put(discriminatorNames[i], discriminatorValue);
                }
            }
            return discriminatorTypes;
        }
        return Collections.emptyMap();
    }



}