<#assign game=pack.page.gametype.game>
<#assign gametype=pack.page.gametype>

<#assign headerbg>${staticPath()}/images/games/${game.name}.png</#assign>

<#list pack.pack.attachments as a>
	<#if a.type == "IMAGE">
		<#assign headerbg=urlEncode(a.url)>
		<#break>
	</#if>
</#list>

<#assign ogDescription="${pack.pack.name}, a ${gametype.name} map pack for ${game.game.bigName} containing ${pack.pack.maps?size} maps, created by ${pack.pack.author}">
<#assign ogImage=headerbg>

<#include "../../_header.ftl">
<#include "../../macros.ftl">

	<@heading bg=[ogImage]>
		<a href="${relPath(sectionPath + "/index.html")}">Map Packs</a>
		/ <a href="${relPath(game.path + "/index.html")}">${game.name}</a>
		/ <a href="${relPath(gametype.path + "/index.html")}">${gametype.name}</a>
		/ ${pack.pack.name}
	</@heading>

	<@content class="info">

		<div class="screenshots">
			<@screenshots attachments=pack.pack.attachments/>
		</div>

		<div class="info">

			<#assign
			labels=[
				"Name",
				"Game Type",
				"Maps",
				"Author",
				"Release (est)",
				"File Size",
				"File Name",
				"Hash"
			]

			values=[
				'${pack.pack.name}',
				'<a href="${relPath(gametype.path + "/index.html")}">${pack.pack.gametype}</a>'?no_esc,
				'${pack.pack.maps?size}',
				'${pack.pack.author}',
				'${dateFmtShort(pack.pack.releaseDate)}',
				'${fileSize(pack.pack.fileSize)}',
				'${pack.pack.originalFilename}',
				'${pack.pack.hash}'
			]

      styles={"7": "nomobile"}
			>

			<@meta title="Map Pack Information" labels=labels values=values styles=styles/>

			<#if pack.variations?size gt 0>
				<section class="variations">
					<h2><img src="${staticPath()}/images/icons/black/px22/variant.png" alt="Variations"/>Variations</h2>
					<table>
						<thead>
						<tr>
							<th>Name</th>
							<th>Release Date (est)</th>
							<th>File Name</th>
							<th>File Size</th>
						</tr>
						</thead>
						<tbody>
							<#list pack.variations as v>
							<tr>
								<td><a href="${relPath(v.path + ".html")}">${v.pack.name}</a></td>
								<td>${v.pack.releaseDate}</td>
								<td>${v.pack.originalFilename}</td>
								<td>${fileSize(v.pack.fileSize)}</td>
							</tr>
							</#list>
						</tbody>
					</table>
				</section>
			</#if>

			<section class="maps">
				<h2><img src="${staticPath()}/images/icons/black/px22/list.png" alt="Maps"/>Maps</h2>
				<table>
					<thead>
					<tr>
						<th>Name</th>
						<th class="nomobile">Title</th>
						<th>Author</th>
					</tr>
					</thead>
					<tbody>
						<#list pack.pack.maps as m>
						<tr>
							<td>${m.name}</td>
							<td class="nomobile">${m.title}</td>
							<td>${m.author}</td>
						</tr>
						</#list>
					</tbody>
				</table>
			</section>

			<@files files=pack.pack.files alsoIn=pack.alsoIn otherFiles=pack.pack.otherFiles/>

			<@downloads downloads=pack.pack.downloads/>

		</div>

	</@content>

<#include "../../_footer.ftl">