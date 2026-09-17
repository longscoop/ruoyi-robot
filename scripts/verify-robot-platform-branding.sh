#!/usr/bin/env bash
set -euo pipefail

repo_root=$(git rev-parse --show-toplevel)
cd "$repo_root"

perl <<'PERL'
use strict;
use warnings;

my $legacy_word = 'yu' . 'dao';
my $legacy_group = 'cn.iocoder.' . 'boot';
my $forbidden = qr/\Q$legacy_word\E|\Q$legacy_group\E/i;

# Only the existing five physical tables and their sequences are compatibility
# contracts. A matching path never exempts the rest of that file's contents.
my %demo_classes = (
    demo01_contact => 'demo01/Demo01ContactDO.java',
    demo02_category => 'demo02/Demo02CategoryDO.java',
    demo03_course => 'demo03/Demo03CourseDO.java',
    demo03_grade => 'demo03/Demo03GradeDO.java',
    demo03_student => 'demo03/Demo03StudentDO.java',
);
my $demo_root = 'robot-platform-module-infra/src/main/java/com/robot/platform/'
    . 'module/infra/dal/dataobject/demo/';
my %java_tables = map {
    ($demo_root . $demo_classes{$_}) => $legacy_word . '_' . $_
} keys %demo_classes;
my %sql_files = map { $_ => 1 } (
    'sql/dm/ruoyi-vue-pro-dm8.sql',
    map { "sql/$_/ruoyi-vue-pro.sql" }
        qw(highgo kingbase mysql opengauss oracle postgresql sqlserver)
);
my @physical_ids = map {
    my $table = $legacy_word . '_' . $_;
    ($table, $table . '_seq')
} keys %demo_classes;
my $physical_id = join '|', map { quotemeta $_ }
    sort { length($b) <=> length($a) } @physical_ids;

# These are pre-migration operational values, not display branding. They are
# deliberately allowed only as complete values at their precise file and SQL
# field locations; every other occurrence remains forbidden.
my $api_host = 'http://api-dashboard.' . $legacy_word . '.iocoder.cn';
my $stage_asset_host = 'http://static-vue3.' . $legacy_word . '.iocoder.cn/';
my %environment_values = (
    'robot-platform-ui-admin/.env.dev' => {
        "VITE_BASE_URL='$api_host'" => 1,
        "VITE_MALL_H5_DOMAIN='http://mall.$legacy_word.iocoder.cn'" => 1,
    },
    'robot-platform-ui-admin/.env.prod' => {
        "VITE_MALL_H5_DOMAIN='http://mall.$legacy_word.iocoder.cn'" => 1,
    },
    'robot-platform-ui-admin/.env.stage' => {
        "VITE_BASE_URL='$api_host'" => 1,
        "VITE_BASE_PATH='$stage_asset_host'" => 1,
        "VITE_MALL_H5_DOMAIN='http://mall.$legacy_word.iocoder.cn'" => 1,
    },
    'robot-platform-ui-admin/.env.test' => {
        "VITE_MALL_H5_DOMAIN='http://mall.$legacy_word.iocoder.cn'" => 1,
    },
);
my $mall_asset_host = 'http://mall.' . $legacy_word . '.iocoder.cn/static/images/';
my $consulting_asset_url = 'http://static.' . $legacy_word . '.iocoder.cn/mp/Aix9975.jpeg';
my %external_asset_values = (
    'robot-platform-ui-admin/src/components/DiyEditor/components/mobile/NoticeBar/config.ts' => {
        "iconUrl: '${mall_asset_host}xinjian.png'," => 1,
    },
    'robot-platform-ui-admin/src/components/DiyEditor/components/mobile/TabBar/config.ts' => {
        "iconUrl: '${mall_asset_host}1-001.png'," => 1,
        "activeIconUrl: '${mall_asset_host}1-002.png'" => 1,
        "iconUrl: '${mall_asset_host}2-001.png'," => 1,
        "activeIconUrl: '${mall_asset_host}2-002.png'" => 1,
        "iconUrl: '${mall_asset_host}3-001.png'," => 1,
        "activeIconUrl: '${mall_asset_host}3-002.png'" => 1,
        "iconUrl: '${mall_asset_host}4-001.png'," => 1,
        "activeIconUrl: '${mall_asset_host}4-002.png'" => 1,
    },
    'robot-platform-ui-admin/src/views/Login/components/LoginForm.vue' => {
        "<el-link href=\"${consulting_asset_url}\" target=\"_blank\">" => 1,
    },
);
my $test_host = 'http://test.' . $legacy_word . '.iocoder.cn';
my %external_url_lines = (
    'robot-platform-server/src/main/resources/application.yaml' => {
        "url: http://dashboard.$legacy_word.iocoder.cn # Admin 管理后台 UI 的地址" => 1,
    },
    'robot-platform-module-infra/src/main/java/com/robot/platform/module/infra/controller/admin/file/FileConfigController.http' => {
        qq{    "domain": "$test_host",} => 1,
    },
    'robot-platform-module-infra/src/main/java/com/robot/platform/module/infra/controller/admin/file/vo/file/FilePresignedUrlRespVO.java' => {
        qq{example = "https://test.$legacy_word.iocoder.cn/758d3a5387507358c7236de4c8f96de1c7f5097ff6a7722b34772fb7b76b140f.png")} => 1,
    },
    'robot-platform-ui-admin/src/views/mp/draft/mock.js' => {
        qq{'$test_host/r6ryvl6LrxBU0miaST4Y-pIcmK-zAAId-9TGgy-DrSLhjVuWbuT3ZBjk9K1yQ0Dn.png'} => 1,
        qq{'$test_host/r6ryvl6LrxBU0miaST4Y-pgFtUNLu1foMSAMkoOsrQrTZ8EtTMssBLfTtzP0dfjG.png'} => 1,
        qq{'$test_host/r6ryvl6LrxBU0miaST4Y-jVixJGgnBnkBPRbuVptOW0CHYuQFyiOVNtamctS8xU8.jpg'} => 1,
    },
    'script/docker/docker-compose.yml' => {
        'name: ' . $legacy_word . '-system_mysql' => 1,
        'name: ' . $legacy_word . '-system_redis' => 1,
    },
);
# These are documentation-only namespace examples, not operational endpoints.
my $brand_example_host = 'robot-platform' . '.iocoder.cn';
my %named_example_lines = (
    'robot-platform-ui-admin/README.md' => {
        '* 示例 URL【Vue3 + element-plus】：`http://dashboard-vue3.' . $brand_example_host . '`（命名示例，不表示已部署端点）' => 1,
        '* 示例 URL【Vue3 + vben5.0(ant-design-vue)】：`http://dashboard-vben.' . $brand_example_host . '`（命名示例，不表示已部署端点）' => 1,
        '* 示例 URL【Vue2 + element-ui】：`http://dashboard.' . $brand_example_host . '`（命名示例，不表示已部署端点）' => 1,
        '示例 URL（Vue3 + Element Plus）：`http://dashboard-vue3.' . $brand_example_host . '`（命名示例，不表示已部署端点）' => 1,
    },
);
my %required_compatibility_lines = (
    'script/docker/docker-compose.yml' => {
        'name: ' . $legacy_word . '-system_mysql' => 1,
        'name: ' . $legacy_word . '-system_redis' => 1,
    },
);
my $storage_domain = 'http://test.' . $legacy_word . '.iocoder.cn';
my $minio_domain = 'http://127.0.0.1:9000/' . $legacy_word;
my $plain_storage_domain = '"domain":"' . $storage_domain . '"';
my $escaped_storage_domain = '\\"domain\\":\\"' . $storage_domain . '\\"';
my $plain_minio_domain = '"domain":"' . $minio_domain . '"';
my $escaped_minio_domain = '\\"domain\\":\\"' . $minio_domain . '\\"';
my $plain_bucket = '"bucket":"' . $legacy_word . '"';
my $escaped_bucket = '\\"bucket\\":\\"' . $legacy_word . '\\"';
my %oauth_client_ids = (
    41 => $legacy_word . '-sso-demo-by-code',
    42 => $legacy_word . '-sso-demo-by-password',
);

open my $tracked, '-|', 'git', 'ls-files', '-z'
    or die "Cannot list tracked files: $!\n";
my @paths = split /\0/, do { local $/; <$tracked> };
close $tracked or die "Cannot list tracked files.\n";

my $failed = 0;
my %seen_compatibility_lines;
for my $path (@paths) {
    if ($path =~ $forbidden) {
        print "$path: forbidden legacy path\n";
        $failed = 1;
    }
    open my $file, '<:raw', $path or die "Cannot read $path: $!\n";
    my $content = do { local $/; <$file> } // '';
    close $file or die "Cannot close $path: $!\n";
    next if index($content, "\0") >= 0; # Tracked text only; leave binaries alone.

    my $line_number = 0;
    for my $line (split /\n/, $content) {
        ++$line_number;
        (my $normalized_line = $line) =~ s/^\s+//;
        if (my $required_lines = $required_compatibility_lines{$path}) {
            $seen_compatibility_lines{$path}{$normalized_line} = 1 if $required_lines->{$normalized_line};
        }
        if ($line =~ /robot-platform\.iocoder\.cn/) {
            my $allowed_examples = $named_example_lines{$path};
            if (!$allowed_examples || !$allowed_examples->{$normalized_line}) {
                print "$path:$line_number: operational endpoint must retain its deployed legacy host: $line\n";
                $failed = 1;
            }
        }
        my $remaining = $line;
        if (my $table = $java_tables{$path}) {
            $remaining =~ s/\@TableName\("\Q$table\E"\)//g;
            $remaining =~ s/\@KeySequence\("\Q${table}_seq\E"\)//g;
        } elsif ($sql_files{$path}) {
            # Identifier boundaries reject prefixes, suffixes and old PK names.
            $remaining =~ s/(?<![A-Za-z0-9_\$])(?:$physical_id)(?![A-Za-z0-9_\$])//g;
            if ($line =~ /^\s*INSERT INTO\s+`?infra_file_config`?\b.*\bVALUES\s*\(\s*22\s*,/) {
                # Every allowed value is tied to its immutable storage seed ID.
                $remaining =~ s/\Q$plain_storage_domain\E//g;
                $remaining =~ s/\Q$escaped_storage_domain\E//g;
            } elsif ($line =~ /^\s*INSERT INTO\s+`?infra_file_config`?\b.*\bVALUES\s*\(\s*27\s*,/) {
                $remaining =~ s/\Q$plain_bucket\E//g;
                $remaining =~ s/\Q$escaped_bucket\E//g;
            } elsif ($line =~ /^\s*INSERT INTO\s+`?infra_file_config`?\b.*\bVALUES\s*\(\s*28\s*,/) {
                $remaining =~ s/\Q$plain_minio_domain\E//g;
                $remaining =~ s/\Q$escaped_minio_domain\E//g;
                $remaining =~ s/\Q$plain_bucket\E//g;
                $remaining =~ s/\Q$escaped_bucket\E//g;
            } elsif ($line =~ /^\s*INSERT INTO\s+`?system_notice`?\b.*\bVALUES\s*\(\s*2\s*,/) {
                $remaining =~ s/\Q$storage_domain\E//g;
            } elsif ($line =~ /^\s*INSERT INTO\s+`?system_oauth2_client`?\b/) {
                if ($line =~ /\bVALUES\s*\(\s*(?:1|40|41|42)\s*,/) {
                    $remaining =~ s/\Q$storage_domain\E//g;
                }
                for my $id (keys %oauth_client_ids) {
                    my $client_id = $oauth_client_ids{$id};
                    $remaining =~ s/(\bVALUES\s*\(\Q$id\E\s*,\s*N?')\Q$client_id\E(?=')/$1/g;
                }
            } elsif ($line =~ /^\s*INSERT INTO\s+`?system_users`?\b/) {
                if ($line =~ /\bVALUES\s*\(\s*1\s*,/) {
                    $remaining =~ s/\Q$storage_domain\E//g;
                }
                $remaining =~ s/(\bVALUES\s*\(\s*100\s*,\s*N?')\Q$legacy_word\E(?=')/$1/g;
            }
        } elsif (my $allowed_lines = $environment_values{$path}) {
            # The environment exception requires the complete, exact line.
            $remaining = '' if $allowed_lines->{$line};
        } elsif (my $asset_lines = $external_asset_values{$path}) {
            # External asset URLs are deployed operational identities; permit
            # only their complete, exact source lines.
            $remaining = '' if $asset_lines->{$normalized_line};
        } elsif (my $url_lines = $external_url_lines{$path}) {
            # These request examples and Docker volume names are exact contracts.
            $remaining = '' if $url_lines->{$line} || $url_lines->{$normalized_line};
        }
        if ($remaining =~ $forbidden) {
            print "$path:$line_number:$line\n";
            $failed = 1;
        }
    }
}
for my $path (keys %required_compatibility_lines) {
    for my $line (keys %{ $required_compatibility_lines{$path} }) {
        next if $seen_compatibility_lines{$path}{$line};
        print "$path: required compatibility value is missing: $line\n";
        $failed = 1;
    }
}
warn "Forbidden legacy identifiers remain.\n" if $failed;
exit $failed;
PERL
