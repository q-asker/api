terraform {
  required_providers {
    oci = {
      source  = "oracle/oci"
      version = "~> 6.0"
    }
  }
}

provider "oci" {
  config_file_profile = "DEFAULT"
}

locals {
  api_nsg_id = "ocid1.networksecuritygroup.oc1.ap-chuncheon-1.aaaaaaaap6rb3pbbxgfofgdpx72xtbgqjl5ws2w3bnenkl3mf7u43gzevomq"
  mon_nsg_id = "ocid1.networksecuritygroup.oc1.ap-chuncheon-1.aaaaaaaaamr2hlzkkpi76gg5dqufgszh6yzwf7rnd3ylhiyswaiwa3mscinq"

  # Cloudflare IPv4 대역 (https://www.cloudflare.com/ips-v4/)
  cloudflare_ipv4 = [
    "173.245.48.0/20",
    "103.21.244.0/22",
    "103.22.200.0/22",
    "103.31.4.0/22",
    "141.101.64.0/18",
    "108.162.192.0/18",
    "190.93.240.0/20",
    "188.114.96.0/20",
    "197.234.240.0/22",
    "198.41.128.0/17",
    "162.158.0.0/15",
    "104.16.0.0/13",
    "104.24.0.0/14",
    "172.64.0.0/13",
    "131.0.72.0/22",
  ]

  # HTTP(80) + HTTPS(443) 포트
  web_ports = [80, 443]

  # Cloudflare IP × 포트 조합
  cloudflare_rules = flatten([
    for port in local.web_ports : [
      for cidr in local.cloudflare_ipv4 : {
        port = port
        cidr = cidr
      }
    ]
  ])
}

# 기존 NSG를 data source로 참조 (Terraform이 관리하지 않는 기존 리소스)
data "oci_core_network_security_group" "api_nsg" {
  network_security_group_id = local.api_nsg_id
}

data "oci_core_network_security_group" "mon_nsg" {
  network_security_group_id = local.mon_nsg_id
}

# API 서버: Cloudflare IP 대역에서 80/443 허용
resource "oci_core_network_security_group_security_rule" "cloudflare_ingress" {
  for_each = { for idx, rule in local.cloudflare_rules : "${rule.port}-${replace(rule.cidr, "/", "_")}" => rule }

  network_security_group_id = local.api_nsg_id
  direction                 = "INGRESS"
  protocol                  = "6" # TCP

  source      = each.value.cidr
  source_type = "CIDR_BLOCK"
  stateless   = false

  tcp_options {
    destination_port_range {
      min = each.value.port
      max = each.value.port
    }
  }

  description = "Cloudflare ${each.value.port} from ${each.value.cidr}"
}

# MON 서버: Cloudflare IP 대역에서 3000 허용 (Grafana)
resource "oci_core_network_security_group_security_rule" "mon_cloudflare_ingress" {
  for_each = { for cidr in local.cloudflare_ipv4 : replace(cidr, "/", "_") => cidr }

  network_security_group_id = local.mon_nsg_id
  direction                 = "INGRESS"
  protocol                  = "6" # TCP

  source      = each.value
  source_type = "CIDR_BLOCK"
  stateless   = false

  tcp_options {
    destination_port_range {
      min = 3000
      max = 3000
    }
  }

  description = "Cloudflare 3000 from ${each.value}"
}
