/*
 * Copyright 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package androidx.appsearch.cts.app;

import static androidx.appsearch.app.AppSearchSchema.StringPropertyConfig.INDEXING_TYPE_PREFIXES;
import static androidx.appsearch.app.AppSearchSchema.StringPropertyConfig.TOKENIZER_TYPE_PLAIN;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;

import androidx.annotation.NonNull;
import androidx.appsearch.annotation.Document;
import androidx.appsearch.app.AppSearchSchema;
import androidx.appsearch.app.DocumentClassFactoryRegistry;
import androidx.appsearch.app.GenericDocument;
import androidx.appsearch.app.Migrator;
import androidx.appsearch.app.PackageIdentifier;
import androidx.appsearch.app.SchemaVisibilityConfig;
import androidx.appsearch.app.SetSchemaRequest;
import androidx.appsearch.exceptions.AppSearchException;
import androidx.appsearch.flags.CheckFlagsRule;
import androidx.appsearch.flags.DeviceFlagsValueProvider;
import androidx.appsearch.flags.Flags;
import androidx.appsearch.flags.RequiresFlagsEnabled;
import androidx.appsearch.testutil.AppSearchEmail;
import androidx.collection.ArrayMap;

import com.google.common.collect.ImmutableSet;

import org.junit.Rule;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class SetSchemaRequestCtsTest {
    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Test
    public void testBuildSetSchemaRequest() {
        AppSearchSchema.StringPropertyConfig prop1 =
                new AppSearchSchema.StringPropertyConfig.Builder("prop1")
                        .setCardinality(AppSearchSchema.PropertyConfig.CARDINALITY_OPTIONAL)
                        .setIndexingType(
                                AppSearchSchema.StringPropertyConfig.INDEXING_TYPE_PREFIXES)
                        .setTokenizerType(AppSearchSchema.StringPropertyConfig.TOKENIZER_TYPE_PLAIN)
                        .build();
        AppSearchSchema schema1 =
                new AppSearchSchema.Builder("type1").addProperty(prop1).build();
        AppSearchSchema schema2 =
                new AppSearchSchema.Builder("type2").addProperty(prop1).build();
        AppSearchSchema schema3 =
                new AppSearchSchema.Builder("type3").addProperty(prop1).build();
        AppSearchSchema schema4 =
                new AppSearchSchema.Builder("type4").addProperty(prop1).build();

        PackageIdentifier packageIdentifier =
                new PackageIdentifier("com.package.foo", new byte[]{100});

        SetSchemaRequest request = new SetSchemaRequest.Builder()
                .addSchemas(schema1, schema2)
                .addSchemas(Arrays.asList(schema3, schema4))
                .setSchemaTypeDisplayedBySystem("type2", /*displayed=*/ false)
                .setSchemaTypeVisibilityForPackage("type1", /*visible=*/ true,
                        packageIdentifier)
                .setForceOverride(true)
                .setVersion(142857)
                .build();

        assertThat(request.getSchemas()).containsExactly(schema1, schema2, schema3, schema4);
        assertThat(request.getSchemasNotDisplayedBySystem()).containsExactly("type2");

        assertThat(request.getSchemasVisibleToPackages()).containsExactly(
                "type1", Collections.singleton(packageIdentifier));
        assertThat(request.getVersion()).isEqualTo(142857);
        assertThat(request.isForceOverride()).isTrue();
    }

    @Test
    public void testSetSchemaRequestTypeChanges() {
        AppSearchSchema.StringPropertyConfig requiredProp =
                new AppSearchSchema.StringPropertyConfig.Builder("prop1")
                        .setCardinality(AppSearchSchema.PropertyConfig.CARDINALITY_REQUIRED)
                        .setIndexingType(
                                AppSearchSchema.StringPropertyConfig.INDEXING_TYPE_PREFIXES)
                        .setTokenizerType(AppSearchSchema.StringPropertyConfig.TOKENIZER_TYPE_PLAIN)
                        .build();
        AppSearchSchema schema1 =
                new AppSearchSchema.Builder("type1").addProperty(requiredProp).build();
        AppSearchSchema schema2 =
                new AppSearchSchema.Builder("type2").addProperty(requiredProp).build();
        AppSearchSchema schema3 =
                new AppSearchSchema.Builder("type3").addProperty(requiredProp).build();

        Migrator expectedMigrator1 = new NoOpMigrator();
        Migrator expectedMigrator2 = new NoOpMigrator();
        Migrator expectedMigrator3 = new NoOpMigrator();
        Map<String, Migrator> migratorMap = new ArrayMap<>();
        migratorMap.put("type1", expectedMigrator1);
        migratorMap.put("type2", expectedMigrator2);

        SetSchemaRequest request = new SetSchemaRequest.Builder()
                .addSchemas(schema1, schema2, schema3)
                .setForceOverride(/*forceOverride=*/ true)
                .setMigrators(migratorMap)
                .setMigrator("type3", expectedMigrator3)
                .build();

        assertThat(request.isForceOverride()).isTrue();
        Map<String, Migrator> expectedMigratorMap = new ArrayMap<>();
        expectedMigratorMap.put("type1", expectedMigrator1);
        expectedMigratorMap.put("type2", expectedMigrator2);
        expectedMigratorMap.put("type3", expectedMigrator3);
        assertThat(request.getMigrators()).containsExactlyEntriesIn(expectedMigratorMap);
    }

    @Test
    public void testInvalidSchemaReferences_fromDisplayedBySystem() {
        IllegalArgumentException expected = assertThrows(IllegalArgumentException.class,
                () -> new SetSchemaRequest.Builder().setSchemaTypeDisplayedBySystem(
                        "InvalidSchema", false).build());
        assertThat(expected).hasMessageThat().contains("referenced, but were not added");
    }

    @Test
    public void testInvalidSchemaReferences_fromPackageVisibility() {
        IllegalArgumentException expected = assertThrows(IllegalArgumentException.class,
                () -> new SetSchemaRequest.Builder().setSchemaTypeVisibilityForPackage(
                        "InvalidSchema", /*visible=*/ true, new PackageIdentifier(
                                "com.foo.package", /*sha256Certificate=*/ new byte[]{})).build());
        assertThat(expected).hasMessageThat().contains("referenced, but were not added");
    }

    @Test
    public void testInvalidSchemaReferences_fromPubliclyVisible() {
        IllegalArgumentException expected = assertThrows(IllegalArgumentException.class,
                () -> new SetSchemaRequest.Builder().setPubliclyVisibleSchema("InvalidSchema",
                        new PackageIdentifier("com.foo.package",
                                /*sha256Certificate=*/ new byte[]{})).build());
        assertThat(expected).hasMessageThat().contains("referenced, but were not added");
    }

    @Test
    public void testInvalidSchemaReferences_fromVisibleToConfigs() {
        byte[] sha256cert1 = new byte[32];
        PackageIdentifier packageIdentifier1 = new PackageIdentifier("Email", sha256cert1);
        SchemaVisibilityConfig config = new SchemaVisibilityConfig.Builder()
                .addAllowedPackage(packageIdentifier1)
                .addRequiredPermissions(
                        ImmutableSet.of(SetSchemaRequest.READ_HOME_APP_SEARCH_DATA))
                .build();

        IllegalArgumentException expected = assertThrows(IllegalArgumentException.class,
                () -> new SetSchemaRequest.Builder()
                        .addSchemaTypeVisibleToConfig("InvalidSchema", config)
                        .build());
        assertThat(expected).hasMessageThat().contains("referenced, but were not added");
    }

    @Test
    public void testSetSchemaTypeDisplayedBySystem_displayed() {
        AppSearchSchema schema = new AppSearchSchema.Builder("Schema").build();

        // By default, the schema is displayed.
        SetSchemaRequest request =
                new SetSchemaRequest.Builder().addSchemas(schema).build();
        assertThat(request.getSchemasNotDisplayedBySystem()).isEmpty();

        request = new SetSchemaRequest.Builder()
                .addSchemas(schema).setSchemaTypeDisplayedBySystem("Schema", true).build();
        assertThat(request.getSchemasNotDisplayedBySystem()).isEmpty();
    }

    @Test
    public void testSetSchemaTypeDisplayedBySystem_notDisplayed() {
        AppSearchSchema schema = new AppSearchSchema.Builder("Schema").build();
        SetSchemaRequest request = new SetSchemaRequest.Builder()
                .addSchemas(schema).setSchemaTypeDisplayedBySystem("Schema", false).build();
        assertThat(request.getSchemasNotDisplayedBySystem()).containsExactly("Schema");
    }

    @Test
    public void testSetSchemaTypeVisibleForPermissions() {
        AppSearchSchema schema = new AppSearchSchema.Builder("Schema").build();

        // By default, the schema is displayed.
        SetSchemaRequest request =
                new SetSchemaRequest.Builder().addSchemas(schema).build();
        assertThat(request.getRequiredPermissionsForSchemaTypeVisibility()).isEmpty();

        SetSchemaRequest.Builder setSchemaRequestBuilder = new SetSchemaRequest.Builder()
                .addSchemas(schema)
                .addRequiredPermissionsForSchemaTypeVisibility("Schema",
                        ImmutableSet.of(SetSchemaRequest.READ_SMS, SetSchemaRequest.READ_CALENDAR))
                .addRequiredPermissionsForSchemaTypeVisibility("Schema",
                        ImmutableSet.of(SetSchemaRequest.READ_HOME_APP_SEARCH_DATA));

        request = setSchemaRequestBuilder.build();

        assertThat(request.getRequiredPermissionsForSchemaTypeVisibility())
                .containsExactly("Schema", ImmutableSet.of(
                        ImmutableSet.of(SetSchemaRequest.READ_SMS,
                                SetSchemaRequest.READ_CALENDAR),
                        ImmutableSet.of(SetSchemaRequest
                                .READ_HOME_APP_SEARCH_DATA)));
    }

    @Test
    public void testClearSchemaTypeVisibleForPermissions() {
        SetSchemaRequest.Builder setSchemaRequestBuilder = new SetSchemaRequest.Builder()
                .addSchemas(
                        new AppSearchSchema.Builder("Schema1").build(),
                        new AppSearchSchema.Builder("Schema2").build())
                .addRequiredPermissionsForSchemaTypeVisibility(
                        "Schema1",
                        ImmutableSet.of(SetSchemaRequest.READ_SMS, SetSchemaRequest.READ_CALENDAR))
                .addRequiredPermissionsForSchemaTypeVisibility(
                        "Schema1",
                        ImmutableSet.of(SetSchemaRequest.READ_HOME_APP_SEARCH_DATA))
                .addRequiredPermissionsForSchemaTypeVisibility(
                        "Schema2",
                        ImmutableSet.of(SetSchemaRequest.READ_EXTERNAL_STORAGE));

        SetSchemaRequest request = setSchemaRequestBuilder.build();

        assertThat(request.getRequiredPermissionsForSchemaTypeVisibility())
                .containsExactly(
                        "Schema1", ImmutableSet.of(
                                ImmutableSet.of(
                                        SetSchemaRequest.READ_SMS, SetSchemaRequest.READ_CALENDAR),
                                ImmutableSet.of(
                                        SetSchemaRequest.READ_HOME_APP_SEARCH_DATA)),
                        "Schema2", ImmutableSet.of(
                                ImmutableSet.of(SetSchemaRequest.READ_EXTERNAL_STORAGE)
                        )
                );

        // Clear the permissions in the builder
        setSchemaRequestBuilder.clearRequiredPermissionsForSchemaTypeVisibility("Schema1");

        // New object should be updated
        assertThat(setSchemaRequestBuilder.build().getRequiredPermissionsForSchemaTypeVisibility())
                .containsExactly(
                        "Schema2", ImmutableSet.of(
                                ImmutableSet.of(SetSchemaRequest.READ_EXTERNAL_STORAGE)
                        )
                );

        // Old object should remain unchanged
        assertThat(request.getRequiredPermissionsForSchemaTypeVisibility())
                .containsExactly(
                        "Schema1", ImmutableSet.of(
                                ImmutableSet.of(
                                        SetSchemaRequest.READ_SMS, SetSchemaRequest.READ_CALENDAR),
                                ImmutableSet.of(
                                        SetSchemaRequest.READ_HOME_APP_SEARCH_DATA)),
                        "Schema2", ImmutableSet.of(
                                ImmutableSet.of(SetSchemaRequest.READ_EXTERNAL_STORAGE)
                        )
                );
    }

    @Test
    public void testSchemaTypeVisibilityForPackage_visible() {
        AppSearchSchema schema = new AppSearchSchema.Builder("Schema").build();

        // By default, the schema is not visible.
        SetSchemaRequest request =
                new SetSchemaRequest.Builder().addSchemas(schema).build();
        assertThat(request.getSchemasVisibleToPackages()).isEmpty();

        PackageIdentifier packageIdentifier = new PackageIdentifier("com.package.foo",
                new byte[]{100});

        request =
                new SetSchemaRequest.Builder().addSchemas(schema).setSchemaTypeVisibilityForPackage(
                        "Schema", /*visible=*/ true, packageIdentifier).build();
        assertThat(request.getSchemasVisibleToPackages()).containsExactly(
                "Schema", Collections.singleton(packageIdentifier));
    }

    @Test
    public void testSchemaTypeVisibilityForPackage_notVisible() {
        AppSearchSchema schema = new AppSearchSchema.Builder("Schema").build();

        SetSchemaRequest request =
                new SetSchemaRequest.Builder().addSchemas(schema).setSchemaTypeVisibilityForPackage(
                        "Schema", /*visible=*/ false, new PackageIdentifier("com.package.foo",
                                /*sha256Certificate=*/ new byte[]{})).build();
        assertThat(request.getSchemasVisibleToPackages()).isEmpty();
    }

    @Test
    public void testSchemaTypeVisibilityForPackage_deduped() throws Exception {
        AppSearchSchema schema = new AppSearchSchema.Builder("Schema").build();

        PackageIdentifier packageIdentifier = new PackageIdentifier("com.package.foo",
                new byte[]{100});

        SetSchemaRequest request =
                new SetSchemaRequest.Builder()
                        .addSchemas(schema)
                        // Set it visible for "Schema"
                        .setSchemaTypeVisibilityForPackage("Schema", /*visible=*/
                                true, packageIdentifier)
                        // Set it visible for "Schema" again, which should be a no-op
                        .setSchemaTypeVisibilityForPackage("Schema", /*visible=*/
                                true, packageIdentifier)
                        .build();
        assertThat(request.getSchemasVisibleToPackages()).containsExactly(
                "Schema", Collections.singleton(packageIdentifier));
    }

    @Test
    public void testSchemaTypeVisibilityForPackage_removed() throws Exception {
        AppSearchSchema schema = new AppSearchSchema.Builder("Schema").build();

        SetSchemaRequest request =
                new SetSchemaRequest.Builder()
                        .addSchemas(schema)
                        // First set it as visible
                        .setSchemaTypeVisibilityForPackage("Schema", /*visible=*/
                                true, new PackageIdentifier("com.package.foo",
                                        /*sha256Certificate=*/ new byte[]{100}))
                        // Then make it not visible
                        .setSchemaTypeVisibilityForPackage("Schema", /*visible=*/
                                false, new PackageIdentifier("com.package.foo",
                                        /*sha256Certificate=*/ new byte[]{100}))
                        .build();

        // Nothing should be visible.
        assertThat(request.getSchemasVisibleToPackages()).isEmpty();
    }

    @Test
    public void testPubliclyVisibleSchemaType() {
        AppSearchSchema schema = new AppSearchSchema.Builder("Schema").build();

        PackageIdentifier packageIdentifier =
                new PackageIdentifier("com.package.foo", /*sha256Certificate=*/ new byte[]{});
        SetSchemaRequest request =
                new SetSchemaRequest.Builder().addSchemas(schema).setPubliclyVisibleSchema(
                        "Schema", packageIdentifier).build();
        assertThat(request.getPubliclyVisibleSchemas())
                .containsExactly("Schema", packageIdentifier);
    }

    @Test
    public void testPubliclyVisibleSchemaType_removal() {
        AppSearchSchema schema = new AppSearchSchema.Builder("Schema").build();

        PackageIdentifier packageIdentifier =
                new PackageIdentifier("com.package.foo", /*sha256Certificate=*/ new byte[]{});
        SetSchemaRequest request =
                new SetSchemaRequest.Builder().addSchemas(schema).setPubliclyVisibleSchema(
                        "Schema", packageIdentifier).build();
        assertThat(request.getPubliclyVisibleSchemas())
                .containsExactly("Schema", packageIdentifier);

        // Removed Schema
        request = new SetSchemaRequest.Builder().addSchemas(schema)
                .setPubliclyVisibleSchema("Schema", packageIdentifier)
                .setPubliclyVisibleSchema("Schema", null)
                .build();
        assertThat(request.getPubliclyVisibleSchemas()).isEmpty();
    }

    @Test
    public void testPubliclyVisibleSchemaType_deduped() {
        AppSearchSchema schema = new AppSearchSchema.Builder("Schema").build();

        PackageIdentifier packageIdentifier =
                new PackageIdentifier("com.package.foo", /*sha256Certificate=*/ new byte[]{});
        PackageIdentifier packageIdentifier2 =
                new PackageIdentifier("com.package.bar", /*sha256Certificate=*/ new byte[]{});
        SetSchemaRequest request =
                new SetSchemaRequest.Builder().addSchemas(schema).setPubliclyVisibleSchema(
                        "Schema", packageIdentifier).build();
        assertThat(request.getPubliclyVisibleSchemas())
                .containsExactly("Schema", packageIdentifier);

        // Deduped schema
        request = new SetSchemaRequest.Builder().addSchemas(schema)
                .setPubliclyVisibleSchema("Schema", packageIdentifier2)
                .setPubliclyVisibleSchema("Schema", packageIdentifier)
                .build();
        assertThat(request.getPubliclyVisibleSchemas())
                .containsExactly("Schema", packageIdentifier);
    }

    @Test
    public void testSetSchemaTypeVisibleForConfigs() {
        AppSearchSchema schema = new AppSearchSchema.Builder("Schema").build();

        PackageIdentifier packageIdentifier1 = new PackageIdentifier("com.package.foo",
                new byte[]{100});
        PackageIdentifier packageIdentifier2 = new PackageIdentifier("com.package.bar",
                new byte[]{100});

        SchemaVisibilityConfig config1 = new SchemaVisibilityConfig.Builder()
                .addAllowedPackage(packageIdentifier1)
                .addRequiredPermissions(
                        ImmutableSet.of(SetSchemaRequest.READ_HOME_APP_SEARCH_DATA))
                .build();
        SchemaVisibilityConfig config2 = new SchemaVisibilityConfig.Builder()
                .addAllowedPackage(packageIdentifier2)
                .addRequiredPermissions(ImmutableSet.of(
                        SetSchemaRequest.READ_HOME_APP_SEARCH_DATA,
                        SetSchemaRequest.READ_CALENDAR))
                .build();

        SetSchemaRequest request = new SetSchemaRequest.Builder()
                .addSchemas(schema)
                .addSchemaTypeVisibleToConfig("Schema", config1)
                .addSchemaTypeVisibleToConfig("Schema", config2)
                .build();

        assertThat(request.getSchemasVisibleToConfigs()).containsExactly("Schema",
                ImmutableSet.of(config1, config2));
    }

    @Test
    public void testClearSchemaTypeVisibleForConfigs() {
        AppSearchSchema schema = new AppSearchSchema.Builder("Schema").build();

        PackageIdentifier packageIdentifier1 = new PackageIdentifier("com.package.foo",
                new byte[]{100});
        PackageIdentifier packageIdentifier2 = new PackageIdentifier("com.package.bar",
                new byte[]{100});

        SchemaVisibilityConfig config1 = new SchemaVisibilityConfig.Builder()
                .addAllowedPackage(packageIdentifier1)
                .addRequiredPermissions(
                        ImmutableSet.of(SetSchemaRequest.READ_HOME_APP_SEARCH_DATA))
                .build();
        SchemaVisibilityConfig config2 = new SchemaVisibilityConfig.Builder()
                .addAllowedPackage(packageIdentifier2)
                .addRequiredPermissions(ImmutableSet.of(
                        SetSchemaRequest.READ_HOME_APP_SEARCH_DATA,
                        SetSchemaRequest.READ_CALENDAR))
                .build();

        SetSchemaRequest.Builder builder = new SetSchemaRequest.Builder()
                .addSchemas(schema)
                .addSchemaTypeVisibleToConfig("Schema", config1)
                .addSchemaTypeVisibleToConfig("Schema", config2);

        SetSchemaRequest original = builder.build();
        assertThat(original.getSchemasVisibleToConfigs()).containsExactly("Schema",
                ImmutableSet.of(config1, config2));

        builder.clearSchemaTypeVisibleToConfigs("Schema");
        SetSchemaRequest rebuild = builder.build();

        // rebuild has empty visible to configs
        assertThat(rebuild.getSchemasVisibleToConfigs()).isEmpty();
        // original keep in the same state
        assertThat(original.getSchemasVisibleToConfigs()).containsExactly("Schema",
                ImmutableSet.of(config1, config2));
    }

    // @exportToFramework:startStrip()
    @Document
    static class Card {
        @Document.Namespace
        String mNamespace;

        @Document.Id
        String mId;

        @Document.StringProperty
                (indexingType = INDEXING_TYPE_PREFIXES, tokenizerType = TOKENIZER_TYPE_PLAIN)
        String mString;

        @Override
        public boolean equals(Object other) {
            if (this == other) {
                return true;
            }
            if (!(other instanceof Card)) {
                return false;
            }
            Card otherCard = (Card) other;
            assertThat(otherCard.mNamespace).isEqualTo(this.mNamespace);
            assertThat(otherCard.mId).isEqualTo(this.mId);
            return true;
        }
    }

    static class Spade {}

    @Document
    static class King extends Spade {
        @Document.Id
        String mId;

        @Document.Namespace
        String mNamespace;

        @Document.StringProperty
                (indexingType = INDEXING_TYPE_PREFIXES, tokenizerType = TOKENIZER_TYPE_PLAIN)
        String mString;
    }

    @Document
    static class Queen extends Spade {
        @Document.Namespace
        String mNamespace;

        @Document.Id
        String mId;

        @Document.StringProperty
                (indexingType = INDEXING_TYPE_PREFIXES, tokenizerType = TOKENIZER_TYPE_PLAIN)
        String mString;
    }

    private static Collection<String> getSchemaTypesFromSetSchemaRequest(SetSchemaRequest request) {
        HashSet<String> schemaTypes = new HashSet<>();
        for (AppSearchSchema schema : request.getSchemas()) {
            schemaTypes.add(schema.getSchemaType());
        }
        return schemaTypes;
    }

    @Test
    public void testSetDocumentClassDisplayedBySystem_displayed() throws Exception {
        // By default, the schema is displayed.
        SetSchemaRequest request =
                new SetSchemaRequest.Builder().addDocumentClasses(Card.class).build();
        assertThat(request.getSchemasNotDisplayedBySystem()).isEmpty();

        request =
                new SetSchemaRequest.Builder().addDocumentClasses(
                        Card.class).setDocumentClassDisplayedBySystem(
                        Card.class, true).build();
        assertThat(request.getSchemasNotDisplayedBySystem()).isEmpty();
    }

    @Test
    public void testSetDocumentClassDisplayedBySystem_notDisplayed() throws Exception {
        SetSchemaRequest request =
                new SetSchemaRequest.Builder().addDocumentClasses(
                        Card.class).setDocumentClassDisplayedBySystem(
                        Card.class, false).build();
        assertThat(request.getSchemasNotDisplayedBySystem()).containsExactly("Card");
    }

    @Test
    public void testSetDocumentClassVisibleForPermission() throws Exception {
        // By default, the schema is displayed.
        SetSchemaRequest request =
                new SetSchemaRequest.Builder().addDocumentClasses(Card.class).build();
        assertThat(request.getRequiredPermissionsForSchemaTypeVisibility()).isEmpty();

        SetSchemaRequest.Builder setSchemaRequestBuilder = new SetSchemaRequest.Builder()
                .addDocumentClasses(Card.class)
                .addRequiredPermissionsForDocumentClassVisibility(Card.class,
                        ImmutableSet.of(SetSchemaRequest.READ_SMS, SetSchemaRequest.READ_CALENDAR))
                .addRequiredPermissionsForDocumentClassVisibility(Card.class,
                        ImmutableSet.of(SetSchemaRequest.READ_HOME_APP_SEARCH_DATA));
        request = setSchemaRequestBuilder.build();

        assertThat(request.getRequiredPermissionsForSchemaTypeVisibility())
                .containsExactly("Card", ImmutableSet.of(
                        ImmutableSet.of(SetSchemaRequest.READ_SMS,
                                SetSchemaRequest.READ_CALENDAR),
                        ImmutableSet.of(SetSchemaRequest
                                .READ_HOME_APP_SEARCH_DATA)));
    }

    @Test
    public void testSetDocumentClassVisibilityForPackage_visible() throws Exception {
        // By default, the schema is not visible.
        SetSchemaRequest request =
                new SetSchemaRequest.Builder().addDocumentClasses(Card.class).build();
        assertThat(request.getSchemasVisibleToPackages()).isEmpty();

        PackageIdentifier packageIdentifier = new PackageIdentifier("com.package.foo",
                new byte[]{100});
        Map<String, Set<PackageIdentifier>> expectedVisibleToPackagesMap = new ArrayMap<>();
        expectedVisibleToPackagesMap.put("Card", Collections.singleton(packageIdentifier));

        request =
                new SetSchemaRequest.Builder().addDocumentClasses(
                        Card.class).setDocumentClassVisibilityForPackage(
                        Card.class, /*visible=*/ true, packageIdentifier).build();
        assertThat(request.getSchemasVisibleToPackages()).containsExactlyEntriesIn(
                expectedVisibleToPackagesMap);
    }

    @Test
    public void testSetDocumentClassVisibilityForPackage_notVisible() throws Exception {
        SetSchemaRequest request =
                new SetSchemaRequest.Builder().addDocumentClasses(
                        Card.class).setDocumentClassVisibilityForPackage(
                        Card.class, /*visible=*/ false,
                        new PackageIdentifier("com.package.foo", /*sha256Certificate=*/
                                new byte[]{})).build();
        assertThat(request.getSchemasVisibleToPackages()).isEmpty();
    }

    @Test
    public void testSetDocumentClassVisibilityForPackage_deduped() throws Exception {
        // By default, the schema is not visible.
        SetSchemaRequest request =
                new SetSchemaRequest.Builder().addDocumentClasses(Card.class).build();
        assertThat(request.getSchemasVisibleToPackages()).isEmpty();

        PackageIdentifier packageIdentifier = new PackageIdentifier("com.package.foo",
                new byte[]{100});
        Map<String, Set<PackageIdentifier>> expectedVisibleToPackagesMap = new ArrayMap<>();
        expectedVisibleToPackagesMap.put("Card", Collections.singleton(packageIdentifier));

        request =
                new SetSchemaRequest.Builder()
                        .addDocumentClasses(Card.class)
                        .setDocumentClassVisibilityForPackage(Card.class, /*visible=*/
                                true, packageIdentifier)
                        .setDocumentClassVisibilityForPackage(Card.class, /*visible=*/
                                true, packageIdentifier)
                        .build();
        assertThat(request.getSchemasVisibleToPackages()).containsExactlyEntriesIn(
                expectedVisibleToPackagesMap);
    }

    @Test
    public void testSetDocumentClassVisibilityForPackage_removed() throws Exception {
        // By default, the schema is not visible.
        SetSchemaRequest request =
                new SetSchemaRequest.Builder().addDocumentClasses(Card.class).build();
        assertThat(request.getSchemasVisibleToPackages()).isEmpty();

        request =
                new SetSchemaRequest.Builder()
                        .addDocumentClasses(Card.class)
                        // First set it as visible
                        .setDocumentClassVisibilityForPackage(Card.class, /*visible=*/
                                true, new PackageIdentifier("com.package.foo",
                                        /*sha256Certificate=*/ new byte[]{100}))
                        // Then make it not visible
                        .setDocumentClassVisibilityForPackage(Card.class, /*visible=*/
                                false, new PackageIdentifier("com.package.foo",
                                        /*sha256Certificate=*/ new byte[]{100}))
                        .build();

        // Nothing should be visible.
        assertThat(request.getSchemasVisibleToPackages()).isEmpty();
    }

    @Test
    public void testAddDocumentClasses_byCollection() throws Exception {
        Set<Class<? extends Spade>> cardClasses = ImmutableSet.of(Queen.class, King.class);
        SetSchemaRequest request =
                new SetSchemaRequest.Builder().addDocumentClasses(cardClasses)
                        .build();
        assertThat(getSchemaTypesFromSetSchemaRequest(request)).containsExactly("Queen",
                "King");
    }

    @Test
    public void testAddDocumentClasses_byCollectionWithSeparateCalls() throws
            Exception {
        SetSchemaRequest request =
                new SetSchemaRequest.Builder().addDocumentClasses(ImmutableSet.of(Queen.class))
                        .addDocumentClasses(ImmutableSet.of(King.class)).build();
        assertThat(getSchemaTypesFromSetSchemaRequest(request)).containsExactly("Queen",
                "King");
    }

    @Test
    public void testClearDocumentClassVisibleForPermissions() throws Exception {
        SetSchemaRequest.Builder setSchemaRequestBuilder = new SetSchemaRequest.Builder()
                .addDocumentClasses(King.class, Queen.class)
                .addRequiredPermissionsForDocumentClassVisibility(
                        King.class,
                        ImmutableSet.of(SetSchemaRequest.READ_SMS, SetSchemaRequest.READ_CALENDAR))
                .addRequiredPermissionsForDocumentClassVisibility(
                        King.class,
                        ImmutableSet.of(SetSchemaRequest.READ_HOME_APP_SEARCH_DATA))
                .addRequiredPermissionsForDocumentClassVisibility(
                        Queen.class,
                        ImmutableSet.of(SetSchemaRequest.READ_EXTERNAL_STORAGE));

        SetSchemaRequest request = setSchemaRequestBuilder.build();

        assertThat(request.getRequiredPermissionsForSchemaTypeVisibility())
                .containsExactly(
                        "King", ImmutableSet.of(
                                ImmutableSet.of(
                                        SetSchemaRequest.READ_SMS, SetSchemaRequest.READ_CALENDAR),
                                ImmutableSet.of(
                                        SetSchemaRequest.READ_HOME_APP_SEARCH_DATA)),
                        "Queen", ImmutableSet.of(
                                ImmutableSet.of(SetSchemaRequest.READ_EXTERNAL_STORAGE)
                        )
                );

        // Clear the permissions in the builder
        setSchemaRequestBuilder.clearRequiredPermissionsForDocumentClassVisibility(King.class);

        // New object should be updated
        assertThat(setSchemaRequestBuilder.build().getRequiredPermissionsForSchemaTypeVisibility())
                .containsExactly(
                        "Queen", ImmutableSet.of(
                                ImmutableSet.of(SetSchemaRequest.READ_EXTERNAL_STORAGE)
                        )
                );

        // Old object should remain unchanged
        assertThat(request.getRequiredPermissionsForSchemaTypeVisibility())
                .containsExactly(
                        "King", ImmutableSet.of(
                                ImmutableSet.of(
                                        SetSchemaRequest.READ_SMS, SetSchemaRequest.READ_CALENDAR),
                                ImmutableSet.of(
                                        SetSchemaRequest.READ_HOME_APP_SEARCH_DATA)),
                        "Queen", ImmutableSet.of(
                                ImmutableSet.of(SetSchemaRequest.READ_EXTERNAL_STORAGE)
                        )
                );
    }

    @Test
    public void testSetDocumentClassVisibleForConfigs() throws Exception {
        PackageIdentifier packageIdentifier1 = new PackageIdentifier("com.package.foo",
                new byte[]{100});
        PackageIdentifier packageIdentifier2 = new PackageIdentifier("com.package.bar",
                new byte[]{100});

        SchemaVisibilityConfig config1 = new SchemaVisibilityConfig.Builder()
                .addAllowedPackage(packageIdentifier1)
                .addRequiredPermissions(
                        ImmutableSet.of(SetSchemaRequest.READ_HOME_APP_SEARCH_DATA))
                .build();
        SchemaVisibilityConfig config2 = new SchemaVisibilityConfig.Builder()
                .addAllowedPackage(packageIdentifier2)
                .addRequiredPermissions(ImmutableSet.of(
                        SetSchemaRequest.READ_HOME_APP_SEARCH_DATA,
                        SetSchemaRequest.READ_CALENDAR))
                .build();

        SetSchemaRequest request = new SetSchemaRequest.Builder()
                .addDocumentClasses(King.class, Queen.class)
                .addDocumentClassVisibleToConfig(King.class, config1)
                .addDocumentClassVisibleToConfig(King.class, config2)
                .build();

        assertThat(request.getSchemasVisibleToConfigs()).containsExactly("King",
                ImmutableSet.of(config1, config2));
    }

    @Test
    public void testClearDocumentClassVisibleForConfigs() throws Exception {
        PackageIdentifier packageIdentifier1 = new PackageIdentifier("com.package.foo",
                new byte[]{100});
        PackageIdentifier packageIdentifier2 = new PackageIdentifier("com.package.bar",
                new byte[]{100});

        SchemaVisibilityConfig config1 = new SchemaVisibilityConfig.Builder()
                .addAllowedPackage(packageIdentifier1)
                .addRequiredPermissions(
                        ImmutableSet.of(SetSchemaRequest.READ_HOME_APP_SEARCH_DATA))
                .build();
        SchemaVisibilityConfig config2 = new SchemaVisibilityConfig.Builder()
                .addAllowedPackage(packageIdentifier2)
                .addRequiredPermissions(ImmutableSet.of(
                        SetSchemaRequest.READ_HOME_APP_SEARCH_DATA,
                        SetSchemaRequest.READ_CALENDAR))
                .build();

        SetSchemaRequest.Builder builder = new SetSchemaRequest.Builder()
                .addDocumentClasses(King.class, Queen.class)
                .addDocumentClassVisibleToConfig(King.class, config1)
                .addDocumentClassVisibleToConfig(King.class, config2);

        SetSchemaRequest original = builder.build();

        assertThat(original.getSchemasVisibleToConfigs()).containsExactly("King",
                ImmutableSet.of(config1, config2));

        // Clear the visbleToConfigs
        builder.clearDocumentClassVisibleToConfigs(King.class);
        SetSchemaRequest rebuild = builder.build();

        // rebuild object has empty visibleToConfigs
        assertThat(rebuild.getSchemasVisibleToConfigs()).isEmpty();
        // original keep in same state.
        assertThat(original.getSchemasVisibleToConfigs()).containsExactly("King",
                ImmutableSet.of(config1, config2));
    }
// @exportToFramework:endStrip()

    @Test
    public void testSetVersion() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new SetSchemaRequest.Builder()
                        .addSchemas(AppSearchEmail.SCHEMA).setVersion(0).build());
        assertThat(exception).hasMessageThat().contains("Version must be a positive number");
        SetSchemaRequest request = new SetSchemaRequest.Builder()
                .addSchemas(AppSearchEmail.SCHEMA).setVersion(1).build();
        assertThat(request.getVersion()).isEqualTo(1);
    }

    @Test
    public void testSetVersion_emptyDb() {
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> new SetSchemaRequest.Builder().setVersion(135).build());
        assertThat(exception).hasMessageThat().contains(
                "Cannot set version to the request if schema is empty.");
    }

    @Test
    public void testRebuild() {
        byte[] sha256cert1 = new byte[32];
        byte[] sha256cert2 = new byte[32];
        Arrays.fill(sha256cert1, (byte) 1);
        Arrays.fill(sha256cert2, (byte) 2);
        PackageIdentifier packageIdentifier1 = new PackageIdentifier("Email", sha256cert1);
        PackageIdentifier packageIdentifier2 = new PackageIdentifier("Email", sha256cert2);
        AppSearchSchema schema1 = new AppSearchSchema.Builder("Email1")
                .addProperty(new AppSearchSchema.StringPropertyConfig.Builder("subject")
                        .setCardinality(AppSearchSchema.PropertyConfig.CARDINALITY_OPTIONAL)
                        .setIndexingType(
                                AppSearchSchema.StringPropertyConfig.INDEXING_TYPE_PREFIXES)
                        .setTokenizerType(AppSearchSchema.StringPropertyConfig.TOKENIZER_TYPE_PLAIN)
                        .build()
                ).build();
        AppSearchSchema schema2 = new AppSearchSchema.Builder("Email2")
                .addProperty(new AppSearchSchema.StringPropertyConfig.Builder("subject")
                        .setCardinality(AppSearchSchema.PropertyConfig.CARDINALITY_OPTIONAL)
                        .setIndexingType(
                                AppSearchSchema.StringPropertyConfig.INDEXING_TYPE_PREFIXES)
                        .setTokenizerType(AppSearchSchema.StringPropertyConfig.TOKENIZER_TYPE_PLAIN)
                        .build()
                ).build();

        SetSchemaRequest.Builder builder = new SetSchemaRequest.Builder()
                .addSchemas(schema1)
                .setVersion(37)
                .setSchemaTypeDisplayedBySystem("Email1", /*displayed=*/false)
                .setSchemaTypeVisibilityForPackage(
                        "Email1", /*visible=*/true, packageIdentifier1)
                .addRequiredPermissionsForSchemaTypeVisibility("Email1",
                        ImmutableSet.of(SetSchemaRequest.READ_SMS,
                                SetSchemaRequest.READ_CALENDAR))
                .addRequiredPermissionsForSchemaTypeVisibility("Email1",
                        ImmutableSet.of(SetSchemaRequest.READ_HOME_APP_SEARCH_DATA));

        SetSchemaRequest original = builder.build();
        SetSchemaRequest rebuild = builder.addSchemas(schema2)
                .setVersion(42)
                .setSchemaTypeDisplayedBySystem("Email2", /*displayed=*/false)
                .setSchemaTypeVisibilityForPackage(
                        "Email2", /*visible=*/true, packageIdentifier2)
                .addRequiredPermissionsForSchemaTypeVisibility("Email2",
                        ImmutableSet.of(SetSchemaRequest.READ_CONTACTS,
                                SetSchemaRequest.READ_EXTERNAL_STORAGE))
                .addRequiredPermissionsForSchemaTypeVisibility("Email2",
                        ImmutableSet.of(SetSchemaRequest.READ_ASSISTANT_APP_SEARCH_DATA))
                .build();

        assertThat(original.getSchemas()).containsExactly(schema1);
        assertThat(original.getVersion()).isEqualTo(37);
        assertThat(original.getSchemasNotDisplayedBySystem()).containsExactly("Email1");
        assertThat(original.getSchemasVisibleToPackages()).containsExactly(
                "Email1", ImmutableSet.of(packageIdentifier1));
        assertThat(original.getRequiredPermissionsForSchemaTypeVisibility()).containsExactly(
                "Email1",
                ImmutableSet.of(
                        ImmutableSet.of(SetSchemaRequest.READ_SMS,
                                SetSchemaRequest.READ_CALENDAR),
                        ImmutableSet.of(SetSchemaRequest.READ_HOME_APP_SEARCH_DATA)));

        assertThat(rebuild.getSchemas()).containsExactly(schema1, schema2);
        assertThat(rebuild.getVersion()).isEqualTo(42);
        assertThat(rebuild.getSchemasNotDisplayedBySystem()).containsExactly("Email1", "Email2");
        assertThat(rebuild.getSchemasVisibleToPackages()).containsExactly(
                "Email1", ImmutableSet.of(packageIdentifier1),
                "Email2", ImmutableSet.of(packageIdentifier2));
        assertThat(rebuild.getRequiredPermissionsForSchemaTypeVisibility()).containsExactly(
                "Email1",
                ImmutableSet.of(
                        ImmutableSet.of(SetSchemaRequest.READ_SMS,
                                SetSchemaRequest.READ_CALENDAR),
                        ImmutableSet.of(SetSchemaRequest.READ_HOME_APP_SEARCH_DATA)),
                "Email2",
                ImmutableSet.of(
                        ImmutableSet.of(SetSchemaRequest.READ_CONTACTS,
                                SetSchemaRequest.READ_EXTERNAL_STORAGE),
                        ImmutableSet.of(SetSchemaRequest.READ_ASSISTANT_APP_SEARCH_DATA)));
    }

    @Test
    public void testRebuild_visibleConfigs() {
        byte[] sha256cert1 = new byte[32];
        byte[] sha256cert2 = new byte[32];
        Arrays.fill(sha256cert1, (byte) 1);
        Arrays.fill(sha256cert2, (byte) 2);
        PackageIdentifier packageIdentifier1 = new PackageIdentifier("Email", sha256cert1);
        PackageIdentifier packageIdentifier2 = new PackageIdentifier("Email", sha256cert2);

        SchemaVisibilityConfig config1 = new SchemaVisibilityConfig.Builder()
                .addAllowedPackage(packageIdentifier1)
                .addRequiredPermissions(
                        ImmutableSet.of(SetSchemaRequest.READ_HOME_APP_SEARCH_DATA))
                .build();
        SchemaVisibilityConfig config2 = new SchemaVisibilityConfig.Builder()
                .addAllowedPackage(packageIdentifier2)
                .addRequiredPermissions(ImmutableSet.of(
                        SetSchemaRequest.READ_HOME_APP_SEARCH_DATA,
                        SetSchemaRequest.READ_CALENDAR))
                .build();

        AppSearchSchema schema1 = new AppSearchSchema.Builder("Email1")
                .addProperty(new AppSearchSchema.StringPropertyConfig.Builder("subject")
                        .setCardinality(AppSearchSchema.PropertyConfig.CARDINALITY_OPTIONAL)
                        .setIndexingType(
                                AppSearchSchema.StringPropertyConfig.INDEXING_TYPE_PREFIXES)
                        .setTokenizerType(AppSearchSchema.StringPropertyConfig.TOKENIZER_TYPE_PLAIN)
                        .build()
                ).build();
        AppSearchSchema schema2 = new AppSearchSchema.Builder("Email2")
                .addProperty(new AppSearchSchema.StringPropertyConfig.Builder("subject")
                        .setCardinality(AppSearchSchema.PropertyConfig.CARDINALITY_OPTIONAL)
                        .setIndexingType(
                                AppSearchSchema.StringPropertyConfig.INDEXING_TYPE_PREFIXES)
                        .setTokenizerType(AppSearchSchema.StringPropertyConfig.TOKENIZER_TYPE_PLAIN)
                        .build()
                ).build();

        SetSchemaRequest.Builder builder = new SetSchemaRequest.Builder()
                .addSchemas(schema1)
                .addSchemaTypeVisibleToConfig("Email1", config1);

        SetSchemaRequest original = builder.build();
        SetSchemaRequest rebuild = builder
                .addSchemas(schema2)
                .addSchemaTypeVisibleToConfig("Email2", config2)
                .build();

        assertThat(original.getSchemas()).containsExactly(schema1);
        assertThat(original.getSchemasVisibleToConfigs()).containsExactly(
                "Email1", ImmutableSet.of(config1));

        assertThat(rebuild.getSchemas()).containsExactly(schema1, schema2);
        assertThat(rebuild.getSchemasVisibleToConfigs()).containsExactly(
                "Email1", ImmutableSet.of(config1),
                "Email2", ImmutableSet.of(config2));
    }

    @Test
    public void testSetVisibility_publicVisibility_rebuild() {
        byte[] sha256cert1 = new byte[32];
        byte[] sha256cert2 = new byte[32];
        Arrays.fill(sha256cert1, (byte) 1);
        Arrays.fill(sha256cert2, (byte) 2);
        PackageIdentifier packageIdentifier1 = new PackageIdentifier("Email", sha256cert1);
        PackageIdentifier packageIdentifier2 = new PackageIdentifier("Email", sha256cert2);
        AppSearchSchema schema1 = new AppSearchSchema.Builder("Email1").build();
        AppSearchSchema schema2 = new AppSearchSchema.Builder("Email2").build();

        SetSchemaRequest.Builder builder = new SetSchemaRequest.Builder()
                .addSchemas(schema1).setPubliclyVisibleSchema("Email1", packageIdentifier1);

        SetSchemaRequest original = builder.build();
        SetSchemaRequest rebuild = builder.addSchemas(schema2)
                .setPubliclyVisibleSchema("Email2", packageIdentifier2).build();

        assertThat(original.getSchemas()).containsExactly(schema1);
        assertThat(original.getPubliclyVisibleSchemas())
                .containsExactly("Email1", packageIdentifier1);

        assertThat(rebuild.getSchemas()).containsExactly(schema1, schema2);
        assertThat(original.getPubliclyVisibleSchemas())
                .containsExactly("Email1", packageIdentifier1);
    }

    @Test
    public void getAndModify() {
        byte[] sha256cert1 = new byte[32];
        byte[] sha256cert2 = new byte[32];
        Arrays.fill(sha256cert1, (byte) 1);
        Arrays.fill(sha256cert2, (byte) 2);
        PackageIdentifier packageIdentifier1 = new PackageIdentifier("Email", sha256cert1);
        PackageIdentifier packageIdentifier2 = new PackageIdentifier("Email", sha256cert2);
        AppSearchSchema schema1 = new AppSearchSchema.Builder("Email1")
                .addProperty(new AppSearchSchema.StringPropertyConfig.Builder("subject")
                        .setCardinality(AppSearchSchema.PropertyConfig.CARDINALITY_OPTIONAL)
                        .setIndexingType(
                                AppSearchSchema.StringPropertyConfig.INDEXING_TYPE_PREFIXES)
                        .setTokenizerType(AppSearchSchema.StringPropertyConfig.TOKENIZER_TYPE_PLAIN)
                        .build()
                ).build();

        SetSchemaRequest request = new SetSchemaRequest.Builder()
                .addSchemas(schema1)
                .setVersion(37)
                .setSchemaTypeDisplayedBySystem("Email1", /*displayed=*/false)
                .setSchemaTypeVisibilityForPackage(
                        "Email1", /*visible=*/true, packageIdentifier1)
                .addRequiredPermissionsForSchemaTypeVisibility("Email1",
                        ImmutableSet.of(SetSchemaRequest.READ_SMS, SetSchemaRequest.READ_CALENDAR))
                .addRequiredPermissionsForSchemaTypeVisibility("Email1",
                        ImmutableSet.of(SetSchemaRequest.READ_HOME_APP_SEARCH_DATA))
                .build();

        // get the visibility setting and modify the output object.
        // skip getSchemasNotDisplayedBySystem and getPubliclyVisibleSchemas since they return
        // unmodifiable objects.
        request.getSchemasVisibleToPackages().put("Email2", ImmutableSet.of(packageIdentifier2));
        request.getRequiredPermissionsForSchemaTypeVisibility().put("Email2",
                ImmutableSet.of(ImmutableSet.of(SetSchemaRequest.READ_CALENDAR)));

        // verify we still get the original object.
        assertThat(request.getSchemasVisibleToPackages()).containsExactly("Email1",
                ImmutableSet.of(packageIdentifier1));
        assertThat(request.getRequiredPermissionsForSchemaTypeVisibility()).containsExactly(
                "Email1",
                ImmutableSet.of(
                        ImmutableSet.of(SetSchemaRequest.READ_SMS, SetSchemaRequest.READ_CALENDAR),
                        ImmutableSet.of(SetSchemaRequest.READ_HOME_APP_SEARCH_DATA)));
    }

    @Test
    public void testVerbatimTokenizerType() {
        AppSearchSchema.StringPropertyConfig prop1 =
                new AppSearchSchema.StringPropertyConfig.Builder("prop1")
                        .setCardinality(AppSearchSchema.PropertyConfig.CARDINALITY_OPTIONAL)
                        .setIndexingType(
                                AppSearchSchema.StringPropertyConfig.INDEXING_TYPE_PREFIXES)
                        .setTokenizerType(
                                AppSearchSchema.StringPropertyConfig.TOKENIZER_TYPE_VERBATIM)
                        .build();
        AppSearchSchema schema1 =
                new AppSearchSchema.Builder("type1").addProperty(prop1).build();

        SetSchemaRequest request = new SetSchemaRequest.Builder()
                .addSchemas(schema1)
                .setForceOverride(true)
                .setVersion(142857)
                .build();
        AppSearchSchema[] schemas = request.getSchemas().toArray(new AppSearchSchema[0]);
        assertThat(schemas).hasLength(1);
        List<AppSearchSchema.PropertyConfig> properties = schemas[0].getProperties();
        assertThat(properties).hasSize(1);
        assertThat(((AppSearchSchema.StringPropertyConfig) properties.get(0)).getTokenizerType())
                .isEqualTo(AppSearchSchema.StringPropertyConfig.TOKENIZER_TYPE_VERBATIM);
    }

    @Test
    public void testRfc822TokenizerType() {
        AppSearchSchema.StringPropertyConfig prop1 =
                new AppSearchSchema.StringPropertyConfig.Builder("prop1")
                        .setCardinality(AppSearchSchema.PropertyConfig.CARDINALITY_OPTIONAL)
                        .setIndexingType(
                                AppSearchSchema.StringPropertyConfig.INDEXING_TYPE_PREFIXES)
                        .setTokenizerType(
                                AppSearchSchema.StringPropertyConfig.TOKENIZER_TYPE_RFC822)
                        .build();
        AppSearchSchema schema1 =
                new AppSearchSchema.Builder("type1").addProperty(prop1).build();

        SetSchemaRequest request = new SetSchemaRequest.Builder()
                .addSchemas(schema1)
                .setForceOverride(true)
                .setVersion(142857)
                .build();
        AppSearchSchema[] schemas = request.getSchemas().toArray(new AppSearchSchema[0]);
        assertThat(schemas).hasLength(1);
        List<AppSearchSchema.PropertyConfig> properties = schemas[0].getProperties();
        assertThat(properties).hasSize(1);
        assertThat(((AppSearchSchema.StringPropertyConfig) properties.get(0)).getTokenizerType())
                .isEqualTo(AppSearchSchema.StringPropertyConfig.TOKENIZER_TYPE_RFC822);
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_ADDITIONAL_BUILDER_COPY_CONSTRUCTORS)
    public void testSetSchemaRequestBuilder_copyConstructor() {
        AppSearchSchema.StringPropertyConfig prop1 =
                new AppSearchSchema.StringPropertyConfig.Builder("prop1")
                        .setCardinality(AppSearchSchema.PropertyConfig.CARDINALITY_OPTIONAL)
                        .setIndexingType(
                                AppSearchSchema.StringPropertyConfig.INDEXING_TYPE_PREFIXES)
                        .setTokenizerType(AppSearchSchema.StringPropertyConfig.TOKENIZER_TYPE_PLAIN)
                        .build();
        AppSearchSchema schema1 =
                new AppSearchSchema.Builder("type1").addProperty(prop1).build();
        AppSearchSchema schema2 =
                new AppSearchSchema.Builder("type2").addProperty(prop1).build();
        AppSearchSchema schema3 =
                new AppSearchSchema.Builder("type3").addProperty(prop1).build();
        AppSearchSchema schema4 =
                new AppSearchSchema.Builder("type4").addProperty(prop1).build();

        PackageIdentifier packageIdentifier =
                new PackageIdentifier("com.package.foo", new byte[]{100});

        SetSchemaRequest request = new SetSchemaRequest.Builder()
                .addSchemas(schema1, schema2)
                .addSchemas(Arrays.asList(schema3, schema4))
                .setSchemaTypeDisplayedBySystem("type2", /*displayed=*/ false)
                .setSchemaTypeVisibilityForPackage("type1", /*visible=*/ true,
                        packageIdentifier)
                .addRequiredPermissionsForSchemaTypeVisibility("type3",
                        Collections.singleton(SetSchemaRequest.READ_CONTACTS))
                .setPubliclyVisibleSchema("type4", packageIdentifier)
                .addSchemaTypeVisibleToConfig("type1", new SchemaVisibilityConfig.Builder().build())
                .setMigrator("type2", new NoOpMigrator())
                .setForceOverride(true)
                .setVersion(142857)
                .build();

        SetSchemaRequest requestCopy = new SetSchemaRequest.Builder(request).build();
        assertThat(requestCopy.getSchemas()).isEqualTo(request.getSchemas());
        assertThat(requestCopy.getSchemasNotDisplayedBySystem()).isEqualTo(
                request.getSchemasNotDisplayedBySystem());
        assertThat(requestCopy.getSchemasVisibleToPackages()).isEqualTo(
                request.getSchemasVisibleToPackages());
        assertThat(requestCopy.getRequiredPermissionsForSchemaTypeVisibility()).isEqualTo(
                request.getRequiredPermissionsForSchemaTypeVisibility());
        assertThat(requestCopy.getPubliclyVisibleSchemas()).isEqualTo(
                request.getPubliclyVisibleSchemas());
        assertThat(requestCopy.getSchemasVisibleToConfigs()).isEqualTo(
                request.getSchemasVisibleToConfigs());
        assertThat(requestCopy.getMigrators()).isEqualTo(request.getMigrators());
        assertThat(requestCopy.getVersion()).isEqualTo(request.getVersion());
        assertThat(requestCopy.isForceOverride()).isEqualTo(request.isForceOverride());
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_ADDITIONAL_BUILDER_COPY_CONSTRUCTORS)
    public void testSetSchemaRequestBuilder_copyConstructor_usesDeepCopies() {
        // Previously, the copy constructor did not make deep copies of all fields, so modifying the
        // builder could affect the request that the builder was created from
        AppSearchSchema.StringPropertyConfig prop1 =
                new AppSearchSchema.StringPropertyConfig.Builder("prop1")
                        .setCardinality(AppSearchSchema.PropertyConfig.CARDINALITY_OPTIONAL)
                        .setIndexingType(
                                AppSearchSchema.StringPropertyConfig.INDEXING_TYPE_PREFIXES)
                        .setTokenizerType(AppSearchSchema.StringPropertyConfig.TOKENIZER_TYPE_PLAIN)
                        .build();
        AppSearchSchema schema1 =
                new AppSearchSchema.Builder("type1").addProperty(prop1).build();
        AppSearchSchema schema2 =
                new AppSearchSchema.Builder("type2").addProperty(prop1).build();
        AppSearchSchema schema3 =
                new AppSearchSchema.Builder("type3").addProperty(prop1).build();
        AppSearchSchema schema4 =
                new AppSearchSchema.Builder("type4").addProperty(prop1).build();

        PackageIdentifier packageIdentifier =
                new PackageIdentifier("com.package.foo", new byte[]{100});

        SetSchemaRequest request = new SetSchemaRequest.Builder()
                .addSchemas(schema1, schema2, schema3, schema4)
                .setSchemaTypeVisibilityForPackage("type1", /*visible=*/ true,
                        packageIdentifier)
                .addRequiredPermissionsForSchemaTypeVisibility("type3",
                        Collections.singleton(SetSchemaRequest.READ_CONTACTS))
                .addSchemaTypeVisibleToConfig("type1", new SchemaVisibilityConfig.Builder().build())
                .build();

        PackageIdentifier otherPackageIdentifier =
                new PackageIdentifier("com.package.bar", new byte[]{100});

        // Create a copy builder and modify the visibility settings
        SetSchemaRequest.Builder unused = new SetSchemaRequest.Builder(request)
                .setSchemaTypeVisibilityForPackage("type1", /*visible=*/ true,
                        otherPackageIdentifier)
                .addRequiredPermissionsForSchemaTypeVisibility("type3", Collections.singleton(
                        SetSchemaRequest.READ_SMS))
                .addSchemaTypeVisibleToConfig("type1",
                        new SchemaVisibilityConfig.Builder().addAllowedPackage(
                                otherPackageIdentifier).build());

        // Validate that changing the copy builder did not affect the original request
        assertThat(request.getSchemasVisibleToPackages()).containsExactly("type1",
                Collections.singleton(packageIdentifier));
        assertThat(request.getRequiredPermissionsForSchemaTypeVisibility()).containsExactly("type3",
                Collections.singleton(Collections.singleton(SetSchemaRequest.READ_CONTACTS)));
        assertThat(request.getSchemasVisibleToConfigs()).containsExactly("type1",
                Collections.singleton(new SchemaVisibilityConfig.Builder().build()));
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_ADDITIONAL_BUILDER_COPY_CONSTRUCTORS)
    public void testSetSchemaRequestBuilder_clearSchemas() {
        AppSearchSchema schema1 = new AppSearchSchema.Builder("type1").build();
        AppSearchSchema schema2 = new AppSearchSchema.Builder("type2").build();
        SetSchemaRequest request = new SetSchemaRequest.Builder()
                .addSchemas(schema1, schema2)
                .clearSchemas()
                .build();
        assertThat(request.getSchemas()).isEmpty();
    }

    @Test
    @RequiresFlagsEnabled(Flags.FLAG_ENABLE_ADDITIONAL_BUILDER_COPY_CONSTRUCTORS)
    public void testSetSchemaRequestBuilder_clearMigrators() {
        AppSearchSchema schema1 = new AppSearchSchema.Builder("type1").build();
        AppSearchSchema schema2 = new AppSearchSchema.Builder("type2").build();
        Migrator migrator = new NoOpMigrator();
        SetSchemaRequest request = new SetSchemaRequest.Builder()
                .addSchemas(schema1, schema2)
                .setMigrator("type1", migrator)
                .setMigrator("type2", migrator)
                .clearMigrators()
                .build();
        assertThat(request.getMigrators()).isEmpty();
    }

    /** Migrator that does nothing. */
    private static class NoOpMigrator extends Migrator {
        @Override
        public boolean shouldMigrate(int currentVersion, int finalVersion) {
            return false;
        }

        @NonNull
        @Override
        public GenericDocument onUpgrade(int currentVersion, int finalVersion,
                @NonNull GenericDocument document) {
            return document;
        }

        @NonNull
        @Override
        public GenericDocument onDowngrade(int currentVersion, int finalVersion,
                @NonNull GenericDocument document) {
            return document;
        }
    }

    // @exportToFramework:startStrip()
    @Document
    static class Outer {
        @Document.Id String mId;
        @Document.Namespace String mNamespace;
        @Document.DocumentProperty Middle mMiddle;
    }

    @Document
    static class Middle {
        @Document.Id String mId;
        @Document.Namespace String mNamespace;
        @Document.DocumentProperty Inner mInner;
    }

    @Document
    static class Inner {
        @Document.Id String mId;
        @Document.Namespace String mNamespace;
        @Document.StringProperty String mContents;
    }

    @Test
    public void testNestedSchemas() throws AppSearchException {
        SetSchemaRequest request = new SetSchemaRequest.Builder().addDocumentClasses(Outer.class)
                .setForceOverride(true).build();
        DocumentClassFactoryRegistry registry = DocumentClassFactoryRegistry.getInstance();

        Set<AppSearchSchema> schemas = request.getSchemas();
        assertThat(schemas).hasSize(3);
        assertThat(schemas).contains(registry.getOrCreateFactory(Outer.class).getSchema());
        assertThat(schemas).contains(registry.getOrCreateFactory(Middle.class).getSchema());
        assertThat(schemas).contains(registry.getOrCreateFactory(Inner.class).getSchema());
    }

    @Document
    static class Parent {
        @Document.Id String mId;
        @Document.Namespace String mNamespace;
        @Document.DocumentProperty Person mPerson;
        @Document.DocumentProperty Organization mOrganization;
    }

    @Document
    static class Person {
        @Document.Id String mId;
        @Document.Namespace String mNamespace;
        @Document.DocumentProperty Common mCommon;
    }

    @Document
    static class Organization {
        @Document.Id String mId;
        @Document.Namespace String mNamespace;
        @Document.DocumentProperty Common mCommon;
    }

    @Document
    static class Common {
        @Document.Id String mId;
        @Document.Namespace String mNamespace;
        @Document.StringProperty String mContents;
    }


    @Test
    public void testNestedSchemasMultiplePaths() throws AppSearchException {
        SetSchemaRequest request = new SetSchemaRequest.Builder().addDocumentClasses(Parent.class)
                .setForceOverride(true).build();
        DocumentClassFactoryRegistry registry = DocumentClassFactoryRegistry.getInstance();

        Set<AppSearchSchema> schemas = request.getSchemas();
        assertThat(schemas).hasSize(4);
        assertThat(schemas).contains(registry.getOrCreateFactory(Common.class).getSchema());
        assertThat(schemas).contains(registry.getOrCreateFactory(Organization.class).getSchema());
        assertThat(schemas).contains(registry.getOrCreateFactory(Person.class).getSchema());
        assertThat(schemas).contains(registry.getOrCreateFactory(Parent.class).getSchema());
    }

    @Document
    static class ArtType {
        @Document.Id String mId;
        @Document.Namespace String mNamespace;
        @Document.StringProperty String mType;
    }

    @Document(name = "Artist", parent = {Person.class})
    static class Artist extends Person {
        @Document.StringProperty String mCompany;
        @Document.DocumentProperty ArtType mArtType;
    }

    @Test
    public void testSchemaPolymorphism() throws AppSearchException {
        SetSchemaRequest request = new SetSchemaRequest.Builder().addDocumentClasses(Artist.class)
                .setForceOverride(true).build();
        DocumentClassFactoryRegistry registry = DocumentClassFactoryRegistry.getInstance();

        Set<AppSearchSchema> schemas = request.getSchemas();
        // Artist's dependencies should be automatically added.
        assertThat(schemas).containsExactly(
                registry.getOrCreateFactory(Common.class).getSchema(),
                registry.getOrCreateFactory(Person.class).getSchema(),
                registry.getOrCreateFactory(ArtType.class).getSchema(),
                registry.getOrCreateFactory(Artist.class).getSchema()
        );
    }

    @Document
    static class Thing {
        @Document.Id String mId;
        @Document.Namespace String mNamespace;
        @Document.StringProperty String mHash;
    }

    @Document(name = "Email", parent = Thing.class)
    static class Email extends Thing {
        @Document.StringProperty String mSender;
    }

    @Document(name = "Message", parent = Thing.class)
    static class Message extends Thing {
        @Document.StringProperty String mContent;
    }

    // EmailMessage can choose any class to "extends" from, since Java's type relationship is
    // independent on AppSearch's. In this case, EmailMessage extends Thing to avoid redefining
    // mId, mNamespace and mHash, but it still needs to specify mSender and mContent coming from
    // Email and Message.
    @Document(name = "EmailMessage", parent = {Email.class, Message.class})
    static class EmailMessage extends Thing {
        @Document.StringProperty String mSender;
        @Document.StringProperty String mContent;
    }

    @Test
    public void testSchemaDiamondPolymorphism() throws AppSearchException {
        SetSchemaRequest request = new SetSchemaRequest.Builder().addDocumentClasses(
                        EmailMessage.class)
                .setForceOverride(true).build();
        DocumentClassFactoryRegistry registry = DocumentClassFactoryRegistry.getInstance();

        Set<AppSearchSchema> schemas = request.getSchemas();
        // EmailMessage's dependencies should be automatically added.
        assertThat(schemas).containsExactly(
                registry.getOrCreateFactory(Thing.class).getSchema(),
                registry.getOrCreateFactory(Email.class).getSchema(),
                registry.getOrCreateFactory(Message.class).getSchema(),
                registry.getOrCreateFactory(EmailMessage.class).getSchema()
        );
    }

    @Document
    static class ClassA {
        @Document.Id
        String mId;

        @Document.Namespace
        String mNamespace;

        @Document.DocumentProperty
        ClassB mClassB;
    }

    @Document
    static class ClassB {
        @Document.Id
        String mId;

        @Document.Namespace
        String mNamespace;

        @Document.DocumentProperty
        ClassA mClassA;
    }

    @Test
    public void testNestedSchemasCyclicalReference() throws AppSearchException {
        SetSchemaRequest request = new SetSchemaRequest.Builder()
                .addDocumentClasses(ClassA.class, ClassB.class)
                .setForceOverride(true)
                .build();

        DocumentClassFactoryRegistry registry = DocumentClassFactoryRegistry.getInstance();

        Set<AppSearchSchema> schemas = request.getSchemas();
        assertThat(schemas).hasSize(2);
        assertThat(schemas).contains(registry.getOrCreateFactory(ClassA.class).getSchema());
        assertThat(schemas).contains(registry.getOrCreateFactory(ClassB.class).getSchema());
    }

// @exportToFramework:endStrip()
}
